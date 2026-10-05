package ru.analizer.marketplace.ozon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.integration.model.AccrualDto;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import ru.analizer.sync.infrastructure.entity.Posting;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.analizer.marketplace.ozon.OzonTestFixtures.load;
import static ru.analizer.marketplace.ozon.OzonTestFixtures.num;

/**
 * Полная выгрузка реального дня 2026-09-26 (53 операции), полученная одним запросом
 * {@code /v1/finance/accrual/by-day}.
 *
 * <p>В отличие от файла за 2026-04-10 этот день содержит операцию POSTING с двумя товарами
 * и ITEM-начисления с несколькими SKU, взятые из живого ответа.
 */
class OzonLiveDay2026_09_26Test {

    private static final String FULL_DAY = "fixtures/accruals-2026-09-26-full.json";
    private static final String MULTI_PRODUCT = "fixtures/accruals-2026-09-26-multiproduct.json";

    @Test
    @DisplayName("День вычитан одной страницей: 53 операции, курсор пустой")
    void wholeDayInSinglePage() throws IOException {
        assertThat(load(FULL_DAY)).hasSize(53);
        assertThat(OzonTestFixtures.mapper()
                .readTree(OzonTestFixtures.read(FULL_DAY))
                .get("last_id").asString())
                .isEmpty();
    }

    @Test
    @DisplayName("Все три категории начислений присутствуют в живом ответе")
    void allCategoriesPresent() throws IOException {
        List<AccrualDto> all = load(FULL_DAY);
        assertThat(all.stream().filter(a -> a.category() == AccrualDto.Category.POSTING)).hasSize(12);
        assertThat(all.stream().filter(a -> a.category() == AccrualDto.Category.ITEM)).hasSize(23);
        assertThat(all.stream().filter(a -> a.category() == AccrualDto.Category.NON_ITEM)).hasSize(18);
        assertThat(all.stream().allMatch(a -> a.date().equals(java.time.LocalDate.of(2026, 9, 26)))).isTrue();
    }

    @Test
    @DisplayName("Живой ответ побайтово совпадает с сохранённой фикстурой многотоварного фрагмента")
    void liveResponseMatchesFixture() throws IOException {
        // Фрагмент, присланный как пример, должен точно совпадать с реальной выгрузкой.
        // Иначе тесты на многотоварный случай проверяли бы выдуманные данные.
        List<AccrualDto> live = load(FULL_DAY);
        List<AccrualDto> fixture = load(MULTI_PRODUCT);

        for (AccrualDto expected : fixture) {
            AccrualDto actual = live.stream()
                    .filter(a -> expected.externalId().equals(a.externalId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(
                            "accrual " + expected.externalId() + " отсутствует в живой выгрузке"));

            assertThat(OzonTestJsonSupport.sameJson(actual.rawJson(), expected.rawJson()))
                    .as("accrual %s должен совпадать с фикстурой", expected.externalId())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Формула total_amount работает и на живом ответе")
    void totalAmountDecomposesOnLiveData() throws IOException {
        List<AccrualDto> postings = load(FULL_DAY).stream()
                .filter(a -> a.category() == AccrualDto.Category.POSTING)
                .toList();

        assertThat(postings).isNotEmpty();
        postings.forEach(a -> {
            BigDecimal computed = a.posting().products().stream()
                    .map(p -> num(p.commission() == null ? null : p.commission().salePrice())
                            .add(num(p.commission() == null ? null : p.commission().bonus()))
                            .add(num(p.commission() == null ? null : p.commission().coinvestment()))
                            .add(num(p.deliveryTotalAccrued()))
                            .add(num(p.commission() == null ? null : p.commission().saleCommission())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(computed).isEqualByComparingTo(a.totalAmount());
        });

        load(FULL_DAY).stream()
                .filter(a -> a.category() == AccrualDto.Category.ITEM)
                .forEach(a -> {
                    BigDecimal computed = a.itemFees().stream()
                            .flatMap(f -> f.fees().stream())
                            .map(AccrualDto.FeeDetail::amount)
                            .map(OzonTestFixtures::num)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    assertThat(computed).isEqualByComparingTo(a.totalAmount());
                });

        load(FULL_DAY).stream()
                .filter(a -> a.category() == AccrualDto.Category.NON_ITEM)
                .forEach(a -> assertThat(num(a.nonItemFee().amount()))
                        .isEqualByComparingTo(a.totalAmount()));
    }

    @Test
    @DisplayName("Справочный type_id на уровне операции отсутствует и в живом ответе")
    void accrualLevelTypeIdStillAbsent() throws IOException {
        assertThat(load(FULL_DAY).stream().allMatch(a -> a.typeId() == null)).isTrue();
    }

    @Test
    @DisplayName("Сумма операций за живой день сходится с ручным подсчётом")
    void dayTotalMatchesManualSum() throws IOException {
        List<AccrualDto> all = load(FULL_DAY);

        BigDecimal total = all.stream()
                .map(AccrualDto::totalAmount)
                .map(OzonTestFixtures::num)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("8617.97");

        // Вклад каждой категории подсчитан вручную по сохранённому ответу.
        assertThat(sumOf(all, AccrualDto.Category.POSTING)).isEqualByComparingTo("12503.72");
        assertThat(sumOf(all, AccrualDto.Category.ITEM)).isEqualByComparingTo("-532.17");
        assertThat(sumOf(all, AccrualDto.Category.NON_ITEM)).isEqualByComparingTo("-3353.58");
    }

    private static BigDecimal sumOf(List<AccrualDto> all, AccrualDto.Category category) {
        return all.stream()
                .filter(a -> a.category() == category)
                .map(AccrualDto::totalAmount)
                .map(OzonTestFixtures::num)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
