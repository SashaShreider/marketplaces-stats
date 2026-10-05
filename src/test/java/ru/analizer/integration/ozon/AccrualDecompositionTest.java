package ru.analizer.integration.ozon;

import org.junit.jupiter.api.Test;
import ru.analizer.integration.model.AccrualDto;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.analizer.integration.ozon.OzonTestFixtures.*;

/**
 * Проверка финансовой целостности на реальных ответах OZON.
 *
 * <p>Главное, что здесь проверяется: {@code total_amount} операции полностью раскладывается
 * на составляющие, которые мы сохраняем. Иначе аналитика этапа 2 считала бы «к выплате»
 * по неполным данным, и расхождение с OZON обнаружилось бы только в отчёте.
 */
class AccrualDecompositionTest {

    private static final String DAY_2026_04_10 = "fixtures/accruals-2026-04-10.json";
    private static final String MULTI_PRODUCT = "fixtures/accruals-2026-09-26-multiproduct.json";

    /**
     * POSTING: total_amount = Σ по товарам
     * (sale_price + bonus + coinvestment + delivery.total_accrued + sale_commission).
     */
    @Test
    void postingTotalAmountIsFullyDecomposed() throws IOException {
        List<AccrualDto> postings = load(DAY_2026_04_10).stream()
                .filter(a -> a.category() == AccrualDto.Category.POSTING)
                .toList();

        assertThat(postings).hasSize(26);
        postings.forEach(a -> {
            BigDecimal computed = a.posting().products().stream()
                    .map(p -> num(p.commission() == null ? null : p.commission().salePrice())
                            .add(num(p.commission() == null ? null : p.commission().bonus()))
                            .add(num(p.commission() == null ? null : p.commission().coinvestment()))
                            .add(num(p.deliveryTotalAccrued()))
                            .add(num(p.commission() == null ? null : p.commission().saleCommission())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(computed)
                    .as("accrual %s (unit %s)", a.externalId(), a.unitNumber())
                    .isEqualByComparingTo(a.totalAmount());
        });
    }

    /** ITEM: total_amount = Σ всех начислений по всем SKU операции. */
    @Test
    void itemTotalAmountIsFullyDecomposed() throws IOException {
        List<AccrualDto> items = load(DAY_2026_04_10).stream()
                .filter(a -> a.category() == AccrualDto.Category.ITEM)
                .toList();

        assertThat(items).hasSize(53);
        items.forEach(a -> {
            BigDecimal computed = a.itemFees().stream()
                    .flatMap(f -> f.fees().stream())
                    .map(AccrualDto.FeeDetail::amount)
                    .map(OzonTestFixtures::num)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(computed)
                    .as("accrual %s (unit %s)", a.externalId(), a.unitNumber())
                    .isEqualByComparingTo(a.totalAmount());
        });
    }

    /** NON_ITEM: total_amount = non_item_fee.accrued. */
    @Test
    void nonItemTotalAmountIsFullyDecomposed() throws IOException {
        List<AccrualDto> nonItems = load(DAY_2026_04_10).stream()
                .filter(a -> a.category() == AccrualDto.Category.NON_ITEM)
                .toList();

        assertThat(nonItems).hasSize(15);
        nonItems.forEach(a -> assertThat(num(a.nonItemFee().amount()))
                .as("accrual %s", a.externalId())
                .isEqualByComparingTo(a.totalAmount()));
    }

    /** Сумма услуг доставки сходится с delivery.total_accrued товара. */
    @Test
    void deliveryServicesSumEqualsDeliveryTotal() throws IOException {
        load(DAY_2026_04_10).stream()
                .filter(a -> a.posting() != null)
                .flatMap(a -> a.posting().products().stream())
                .filter(p -> p.deliveryTotalAccrued() != null)
                .forEach(p -> {
                    BigDecimal services = p.deliveryServices().stream()
                            .map(AccrualDto.FeeDetail::amount)
                            .map(OzonTestFixtures::num)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    assertThat(services)
                            .as("sku %s, accrual %s", p.sku(), p.sku())
                            .isEqualByComparingTo(p.deliveryTotalAccrued());
                });
    }

    /**
     * Несколько товаров в одной операции POSTING — реальный ответ за 2026-09-26.
     * В ответе за 2026-04-10 таких операций нет: все 26 POSTING содержат ровно один товар.
     */
    @Test
    void onePostingAccrualCanContainSeveralProducts() throws IOException {
        List<AccrualDto> all = load(MULTI_PRODUCT);
        assertThat(all).hasSize(3);

        AccrualDto posting = all.getFirst();
        assertThat(posting.category()).isEqualTo(AccrualDto.Category.POSTING);
        assertThat(posting.unitNumber()).isEqualTo("04485996-0279-1");
        assertThat(posting.totalAmount()).isEqualByComparingTo("465.96");
        assertThat(posting.posting().products()).hasSize(2);

        AccrualDto.Product first = posting.posting().products().get(0);
        assertThat(first.sku()).isEqualTo(174269728L);
        assertThat(first.quantity()).isEqualTo(1);
        assertThat(first.commission().salePrice()).isEqualByComparingTo("343.94");
        assertThat(first.commission().saleAmount()).isEqualByComparingTo("700");
        assertThat(first.commission().saleCommission()).isEqualByComparingTo("-301");
        assertThat(first.commission().commissionRatio()).isEqualTo("0.430000");
        assertThat(first.deliveryTotalAccrued()).isEqualByComparingTo("-105.02");
        assertThat(first.deliveryServices()).hasSize(3);

        AccrualDto.Product second = posting.posting().products().get(1);
        assertThat(second.sku()).isEqualTo(174269875L);
        assertThat(second.commission().salePrice()).isEqualByComparingTo("188.12");
        assertThat(second.deliveryTotalAccrued()).isEqualByComparingTo("-113.02");

        // Ручная сверка:
        //   343.94 + 352.62 + 3.44 - 105.02 - 301.00 = 293.98
        //   188.12 + 310.00 + 1.88 - 113.02 - 215.00 = 171.98
        //   293.98 + 171.98 = 465.96 = total_amount
        assertThat(componentSum(first)).isEqualByComparingTo("293.98");
        assertThat(componentSum(second)).isEqualByComparingTo("171.98");
    }

    /** Один ITEM может содержать несколько SKU — расход делится между товарами. */
    @Test
    void oneItemAccrualCanContainSeveralSkus() throws IOException {
        List<AccrualDto> all = load(MULTI_PRODUCT);

        AccrualDto starRating = all.get(1);
        assertThat(starRating.category()).isEqualTo(AccrualDto.Category.ITEM);
        assertThat(starRating.unitNumber()).isEqualTo("04485996-0279-1");
        assertThat(starRating.itemFees()).hasSize(2);
        assertThat(starRating.itemFees().stream().map(AccrualDto.ItemFee::sku))
                .containsExactly(174269728L, 174269875L);
        assertThat(starRating.itemFees().stream().flatMap(f -> f.fees().stream())
                .map(AccrualDto.FeeDetail::typeId)).containsExactly(74, 74);
        // type_id 74 — списание за «звёздный товар»: -10.5 + -7.5 = -18
        assertThat(itemSum(starRating)).isEqualByComparingTo(starRating.totalAmount());
        assertThat(starRating.totalAmount()).isEqualByComparingTo("-18");

        AccrualDto acquiring = all.get(2);
        assertThat(acquiring.itemFees()).hasSize(2);
        assertThat(acquiring.itemFees().stream().flatMap(f -> f.fees().stream())
                .map(AccrualDto.FeeDetail::typeId)).containsExactly(1, 1);
        // type_id 1 — эквайринг: -6.53 + -3.57 = -10.10
        assertThat(itemSum(acquiring)).isEqualByComparingTo(acquiring.totalAmount());
        assertThat(acquiring.totalAmount()).isEqualByComparingTo("-10.10");
    }

    /**
     * Одна продажа — несколько финансовых операций с общим unit_number.
     * Вклад платежа в «к выплате» равен сумме total_amount по всем его операциям.
     */
    @Test
    void unitNumberGroupsSeveralAccrualsIntoSinglePayout() throws IOException {
        List<AccrualDto> all = load(MULTI_PRODUCT);

        BigDecimal payout = all.stream().map(AccrualDto::totalAmount)
                .map(OzonTestFixtures::num)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 465.96 (продажа) - 18.00 (звёздный товар) - 10.10 (эквайринг) = 437.86
        assertThat(payout).isEqualByComparingTo("437.86");
        assertThat(all.stream().map(AccrualDto::unitNumber).distinct())
                .containsExactly("04485996-0279-1");
        assertThat(all.stream().map(AccrualDto::externalId).distinct()).hasSize(3);
    }

    /**
     * Тот же сценарий в ответе за 2026-04-10: одна продажа и три ITEM-списания
     * с другими accrual_id, но тем же unit_number.
     */
    @Test
    void existingDayAlsoGroupsAccrualsByUnitNumber() throws IOException {
        List<AccrualDto> forUnit = load(DAY_2026_04_10).stream()
                .filter(a -> "04142187-0153-1".equals(a.unitNumber()))
                .toList();

        BigDecimal payout = forUnit.stream().map(AccrualDto::totalAmount)
                .map(OzonTestFixtures::num)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 595.47 (продажа) - 5.95 - 15.06 - 3.69 = 570.77
        assertThat(payout).isEqualByComparingTo("570.77");
        assertThat(forUnit).hasSize(4);

        Long postingSku = byExternalId(DAY_2026_04_10, 48819114618L)
                .posting().products().getFirst().sku();
        assertThat(forUnit.stream()
                .filter(a -> a.category() == AccrualDto.Category.ITEM)
                .map(a -> a.itemFees().getFirst().sku()))
                .containsExactly(postingSku, postingSku, postingSku);
    }

    private static BigDecimal componentSum(AccrualDto.Product p) {
        return num(p.commission() == null ? null : p.commission().salePrice())
                .add(num(p.commission() == null ? null : p.commission().bonus()))
                .add(num(p.commission() == null ? null : p.commission().coinvestment()))
                .add(num(p.deliveryTotalAccrued()))
                .add(num(p.commission() == null ? null : p.commission().saleCommission()));
    }

    private static BigDecimal itemSum(AccrualDto accrual) {
        return accrual.itemFees().stream()
                .flatMap(f -> f.fees().stream())
                .map(AccrualDto.FeeDetail::amount)
                .map(OzonTestFixtures::num)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
