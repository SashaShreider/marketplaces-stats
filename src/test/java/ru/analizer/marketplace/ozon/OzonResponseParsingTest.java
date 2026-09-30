package ru.analizer.marketplace.ozon;

import org.junit.jupiter.api.Test;
import ru.analizer.marketplace.AccrualDto;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.analizer.marketplace.ozon.OzonTestFixtures.byExternalId;
import static ru.analizer.marketplace.ozon.OzonTestFixtures.load;

/**
 * Разбор реального ответа OZON {@code /v1/finance/accrual/by-day} за 2026-04-10.
 * Фикстура снята с продавца, а не придумана.
 */
class OzonResponseParsingTest {

    private static final String FIXTURE = "example-2026-04-10.json";

    @Test
    void parsesRealResponseWithoutLosingAccruals() throws IOException {
        assertThat(load(FIXTURE)).hasSize(94);

        String root = OzonTestFixtures.read(FIXTURE);
        assertThat(OzonTestFixtures.mapper().readTree(root).get("last_id").asString())
                .as("пустой курсор означает, что день вычитан одной страницей")
                .isEmpty();
    }

    @Test
    void mapsPostingAccrualWithCommissionAndDelivery() throws IOException {
        AccrualDto dto = byExternalId(FIXTURE, 48762627746L);

        assertThat(dto.category()).isEqualTo(AccrualDto.Category.POSTING);
        assertThat(dto.unitNumber()).isEqualTo("31020767-0597-1");
        assertThat(dto.totalAmount()).isEqualByComparingTo("869.30");
        assertThat(dto.currency()).isEqualTo("RUB");

        AccrualDto.Posting posting = dto.posting();
        assertThat(posting).isNotNull();
        assertThat(posting.deliverySchema()).isEqualTo("Fbo");
        assertThat(posting.products()).hasSize(1);

        AccrualDto.Product product = posting.products().getFirst();
        assertThat(product.sku()).isEqualTo(1388985440L);
        assertThat(product.quantity()).isEqualTo(1);

        AccrualDto.Commission commission = product.commission();
        assertThat(commission).isNotNull();
        assertThat(commission.salePrice()).isEqualByComparingTo("871.43");
        assertThat(commission.sellerPrice()).isEqualByComparingTo("1530");
        assertThat(commission.saleAmount()).isEqualByComparingTo("1530");
        // Комиссия хранится со знаком минус — это расход.
        assertThat(commission.saleCommission()).isEqualByComparingTo("-596.70");
        assertThat(commission.commission()).isEqualByComparingTo("-596.70");
        assertThat(commission.coinvestment()).isEqualByComparingTo("8.71");
        assertThat(commission.bonus()).isEqualByComparingTo("649.86");
        // commission_ratio приходит как "value:\"0.390000\"" — извлекаем само значение.
        assertThat(commission.commissionRatio()).isEqualTo("0.390000");

        assertThat(product.deliveryTotalAccrued()).isEqualByComparingTo("-64.00");
        assertThat(product.deliveryServices()).hasSize(2);
        assertThat(product.deliveryServices().get(0).typeId()).isEqualTo(32);
        assertThat(product.deliveryServices().get(0).amount()).isEqualByComparingTo("-56.94");
    }

    @Test
    void mapsPostingAccrualWithoutCommission() throws IOException {
        AccrualDto dto = byExternalId(FIXTURE, 48751325983L);

        assertThat(dto.category()).isEqualTo(AccrualDto.Category.POSTING);
        AccrualDto.Product product = dto.posting().products().getFirst();
        // Начисление только по логистике: commission отсутствует, но delivery есть.
        assertThat(product.commission()).isNull();
        assertThat(product.deliveryServices()).isNotEmpty();
        assertThat(product.deliveryTotalAccrued()).isEqualByComparingTo("-67.11");
    }

    @Test
    void mapsItemAccrual() throws IOException {
        AccrualDto dto = byExternalId(FIXTURE, 48762253857L);

        assertThat(dto.category()).isEqualTo(AccrualDto.Category.ITEM);
        assertThat(dto.unitNumber()).isEqualTo("02321694-0488");
        assertThat(dto.posting()).isNull();
        assertThat(dto.itemFees()).hasSize(1);

        AccrualDto.ItemFee itemFee = dto.itemFees().getFirst();
        assertThat(itemFee.sku()).isEqualTo(3753845770L);
        assertThat(itemFee.quantity()).isEqualTo(1);
        assertThat(itemFee.fees()).hasSize(1);
        assertThat(itemFee.fees().getFirst().typeId()).isEqualTo(1);
        assertThat(itemFee.fees().getFirst().amount()).isEqualByComparingTo("-14.75");
    }

    @Test
    void mapsNonItemAccrualWithoutUnitNumber() throws IOException {
        AccrualDto dto = byExternalId(FIXTURE, 48822624708L);

        assertThat(dto.category()).isEqualTo(AccrualDto.Category.NON_ITEM);
        // У части NON_ITEM начислений unit_number отсутствует.
        assertThat(dto.unitNumber()).isNull();
        assertThat(dto.itemFees()).isNull();
        assertThat(dto.nonItemFee()).isNotNull();
        assertThat(dto.nonItemFee().typeId()).isEqualTo(46);
        assertThat(dto.nonItemFee().amount()).isEqualByComparingTo("-380.68");
    }

    @Test
    void keepsRawJsonForEveryAccrual() throws IOException {
        AccrualDto dto = byExternalId(FIXTURE, 48762627746L);
        assertThat(dto.rawJson()).isNotBlank();

        // Исходный JSON обязан оставаться разбираемым и содержать те же значения,
        // иначе его нельзя будет переинтерпретировать при смене правил аналитики.
        var reparsed = OzonTestFixtures.mapper().readTree(dto.rawJson());
        assertThat(reparsed.get("accrual_id").asLong()).isEqualTo(48762627746L);
        assertThat(reparsed.get("accrued_category").asString()).isEqualTo("POSTING");
    }

    @Test
    void containsSaleReturnAndExpenseScenarios() throws IOException {
        List<AccrualDto.Product> products = load(FIXTURE).stream()
                .filter(a -> a.posting() != null)
                .flatMap(a -> a.posting().products().stream())
                .toList();

        // Возврат: отрицательная sale_price.
        List<AccrualDto.Product> returns = products.stream()
                .filter(p -> p.commission() != null && p.commission().salePrice() != null
                        && p.commission().salePrice().signum() < 0)
                .toList();
        assertThat(returns).hasSize(1);

        AccrualDto.Product returned = returns.getFirst();
        assertThat(returned.commission().salePrice()).isEqualByComparingTo("-1039.05");
        assertThat(returned.commission().saleAmount()).isEqualByComparingTo("-1724.00");
        assertThat(returned.commission().bonus()).isEqualByComparingTo("-674.56");
        // При возврате комиссия возвращается продавцу, то есть становится положительной.
        // Наивное «комиссия всегда расход» сломало бы учёт возвратов.
        assertThat(returned.commission().saleCommission()).isEqualByComparingTo("672.36");
        assertThat(returned.sku()).isEqualTo(1973850522L);

        // Продажи: положительная sale_price.
        assertThat(products.stream()
                .filter(p -> p.commission() != null && p.commission().salePrice() != null)
                .filter(p -> p.commission().salePrice().signum() > 0)
                .count()).isEqualTo(19);

        // Знак комиссии следует за знаком операции.
        assertThat(products.stream()
                .filter(p -> p.commission() != null && p.commission().salePrice() != null)
                .filter(p -> p.commission().salePrice().signum() > 0)
                .allMatch(p -> p.commission().saleCommission().signum() < 0)).isTrue();
        assertThat(products.stream()
                .filter(p -> p.commission() != null && p.commission().salePrice() != null)
                .filter(p -> p.commission().salePrice().signum() < 0)
                .allMatch(p -> p.commission().saleCommission().signum() > 0)).isTrue();

        // commission и sale_commission совпадают.
        assertThat(products.stream()
                .filter(p -> p.commission() != null && p.commission().commission() != null)
                .allMatch(p -> p.commission().commission()
                        .compareTo(p.commission().saleCommission()) == 0)).isTrue();

        // Логистика: у товаров с продажей есть хотя бы одна услуга доставки.
        assertThat(products.stream()
                .filter(p -> p.commission() != null && p.commission().salePrice() != null
                        && p.commission().salePrice().signum() > 0)
                .allMatch(p -> !p.deliveryServices().isEmpty())).isTrue();

        // У начисления по возврату блок delivery приходит null целиком.
        assertThat(returned.deliveryTotalAccrued()).isNull();
        assertThat(returned.deliveryServices()).isEmpty();

        // Прочие расходы: NON_ITEM без привязки к SKU.
        assertThat(load(FIXTURE).stream()
                .anyMatch(a -> a.category() == AccrualDto.Category.NON_ITEM)).isTrue();
    }

    @Test
    void accrualLevelTypeIdIsAbsentInRealResponse() throws IOException {
        // Документация OZON объявляет type_id на уровне операции, но в реальном ответе
        // его нет ни разу: тип начисления лежит в детализации. Тест зафиксирован, чтобы
        // при появлении поля мы узнали об этом, а не получили бы молчаливую потерю.
        assertThat(load(FIXTURE).stream().allMatch(a -> a.typeId() == null)).isTrue();
    }

    @Test
    void totalAmountSumsMatchManualCalculation() throws IOException {
        // Ручная сверка по сохранённому файлу: сумма total_amount за день.
        BigDecimal sum = load(FIXTURE).stream()
                .map(AccrualDto::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("11297.23");
    }
}
