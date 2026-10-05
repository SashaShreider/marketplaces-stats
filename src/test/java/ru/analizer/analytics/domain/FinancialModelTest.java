package ru.analizer.analytics.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.analytics.domain.FeeFact;
import ru.analizer.analytics.domain.FinancialModel;
import ru.analizer.analytics.domain.FinancialSummary;
import ru.analizer.analytics.domain.ProductFact;
import ru.analizer.integration.ozon.dto.finance.Commission;
import ru.analizer.integration.ozon.OzonTestFixturesAccess;
import ru.analizer.integration.ozon.dto.finance.FinanceAccrual;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.analizer.sync.infrastructure.entity.ItemFee;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Финансовая модель на настоящих выгрузках OZON.
 *
 * <p>Каждая цифра сверена вручную по сохранённым ответам API. Модель обязана давать
 * то же самое, что показывает OZON: иначе отчёт будет врать владельцу магазина.
 */
class FinancialModelTest {

    private static final String DAY_2026_04_10 = "fixtures/accruals-2026-04-10.json";
    private static final String DAY_2026_09_26 = "fixtures/accruals-2026-09-26-full.json";

    /** Ручной подсчёт по файлу fixtures/accruals-2026-04-10.json. */
    private static final LocalDate D1 = LocalDate.of(2026, 4, 10);
    private static final String D1_SALES = "19804.67";
    private static final String D1_RETURNS = "-1039.05";
    private static final String D1_PARTNER = "12399.38";
    private static final String D1_COMMISSION = "-12879.33";
    private static final String D1_LOGISTICS = "-3000.71";
    private static final String D1_OTHER = "-3987.73";
    private static final String D1_PAYOUT = "11297.23";

    /** Ручной подсчёт по файлу accruals-2026-09-26-full.json. */
    private static final LocalDate D2 = LocalDate.of(2026, 9, 26);
    private static final String D2_SALES = "11687.27";
    private static final String D2_RETURNS = "0";
    private static final String D2_PARTNER = "13592.73";
    private static final String D2_COMMISSION = "-11326.77";
    private static final String D2_LOGISTICS = "-1449.51";
    private static final String D2_OTHER = "-3885.75";
    private static final String D2_PAYOUT = "8617.97";

    @Test
    @DisplayName("2026-04-10: показатели совпадают с ручным подсчётом")
    void day2026_04_10() {
        FinancialSummary s = summarize(DAY_2026_04_10, D1, D1_PAYOUT);

        assertThat(s.sales()).isEqualByComparingTo(D1_SALES);
        assertThat(s.returns()).isEqualByComparingTo(D1_RETURNS);
        assertThat(s.partnerProgramme()).isEqualByComparingTo(D1_PARTNER);
        assertThat(s.commission()).isEqualByComparingTo(D1_COMMISSION);
        assertThat(s.logistics()).isEqualByComparingTo(D1_LOGISTICS);
        assertThat(s.otherExpenses()).isEqualByComparingTo(D1_OTHER);
        assertThat(s.payout()).isEqualByComparingTo(D1_PAYOUT);
    }

    @Test
    @DisplayName("2026-09-26: показатели совпадают с ручным подсчётом")
    void day2026_09_26() {
        FinancialSummary s = summarize(DAY_2026_09_26, D2, D2_PAYOUT);

        assertThat(s.sales()).isEqualByComparingTo(D2_SALES);
        assertThat(s.returns()).isEqualByComparingTo(D2_RETURNS);
        assertThat(s.partnerProgramme()).isEqualByComparingTo(D2_PARTNER);
        assertThat(s.commission()).isEqualByComparingTo(D2_COMMISSION);
        assertThat(s.logistics()).isEqualByComparingTo(D2_LOGISTICS);
        assertThat(s.otherExpenses()).isEqualByComparingTo(D2_OTHER);
        assertThat(s.payout()).isEqualByComparingTo(D2_PAYOUT);
    }

    @Test
    @DisplayName("Доходы минус расходы равны к выплате — на обоих днях")
    void incomeMinusExpensesEqualsPayout() {
        FinancialSummary d1 = summarize(DAY_2026_04_10, D1, D1_PAYOUT);
        FinancialSummary d2 = summarize(DAY_2026_09_26, D2, D2_PAYOUT);

        assertThat(d1.income()).isEqualByComparingTo("31165.00");
        assertThat(d1.expenses()).isEqualByComparingTo("19867.77");
        assertThat(d1.income().subtract(d1.expenses())).isEqualByComparingTo(d1.payout());
        assertThat(d1.reconciles()).isTrue();

        assertThat(d2.income()).isEqualByComparingTo("25280.00");
        assertThat(d2.expenses()).isEqualByComparingTo("16662.03");
        assertThat(d2.income().subtract(d2.expenses())).isEqualByComparingTo(d2.payout());
        assertThat(d2.reconciles()).isTrue();
    }

    @Test
    @DisplayName("Без учёта бонусов и ковейста отчёт разошёлся бы на их сумму")
    void partnerProgrammeIsRequiredForReconciliation() {
        // Это причина, по которой бонусы вынесены в доходы, а не оставлены без внимания:
        // IMPLEMENTATION.md §15 их не перечисляет, и «к выплате» разошёлся бы ровно
        // на сумму начислений по программе партнёров.
        FinancialSummary d1 = summarize(DAY_2026_04_10, D1, D1_PAYOUT);

        BigDecimal withoutPartner = d1.sales().add(d1.returns())
                .subtract(d1.expenses());
        assertThat(withoutPartner).isEqualByComparingTo("-1102.15");
        assertThat(d1.payout().subtract(withoutPartner))
                .as("разница равна бонусам и ковейсту")
                .isEqualByComparingTo(d1.partnerProgramme());
    }

    @Test
    @DisplayName("Возврат уменьшает доход, а комиссия по нему возвращается")
    void returnDecreasesIncomeAndRefundsCommission() {
        FinancialSummary d1 = summarize(DAY_2026_04_10, D1, D1_PAYOUT);

        assertThat(d1.returns()).as("возврат учитывается отрицательным").isNegative();
        assertThat(d1.returns()).isEqualByComparingTo("-1039.05");

        // Комиссия по возврату приходит положительной и уменьшает расход.
        // Без этого «расходы» были бы завышены на 672.36.
        assertThat(d1.commission()).isNegative();
        assertThat(d1.commission()).isEqualByComparingTo("-12879.33");
    }

    @Test
    @DisplayName("Прочие расходы детализируются по типам начислений, крупные сверху")
    void otherExpensesAreBrokenDownByType() {
        Map<Integer, String> names = Map.of(
                41, "PayPerClick",
                54, "Promotion",
                46, "Placements",
                12, "CrossDock",
                1, "Acquiring",
                74, "StarsMembership",
                48, "PremiumCashbackIndividualPoints",
                39, "PackingFee",
                38, "PackageCost");

        FinancialSummary.ExpenseByType breakdown = FinancialModel.expensesByType(
                feesFrom(DAY_2026_04_10, D1), names);

        assertThat(breakdown.items()).hasSize(9);
        assertThat(breakdown.items().getFirst().name()).isEqualTo("PayPerClick");
        assertThat(breakdown.items().getFirst().amount()).isEqualByComparingTo("-2267.09");
        assertThat(breakdown.items().get(1).amount()).isEqualByComparingTo("-627.85");
        assertThat(breakdown.items().get(8).amount()).isEqualByComparingTo("-5");

        // Сумма детализации обязана равняться прочим расходам дня.
        BigDecimal sum = breakdown.items().stream()
                .map(FinancialSummary.TypeAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(D1_OTHER);
    }

    @Test
    @DisplayName("Логистика не попадает в детализацию прочих расходов")
    void deliveryIsNotInOtherExpenses() {
        FinancialSummary.ExpenseByType breakdown = FinancialModel.expensesByType(
                feesFrom(DAY_2026_04_10, D1), Map.of());

        // Все элементы — только ITEM и NON_ITEM; типы логистики (6, 16, 17, 29, 32, 59, 98)
        // в детализацию не попадают, они отдельно идут строкой «логистика».
        assertThat(breakdown.items()).extracting(FinancialSummary.TypeAmount::typeId)
                .doesNotContain(6, 16, 17, 29, 32, 59, 98);
    }

    @Test
    @DisplayName("Пустой период даёт нули, а не ошибку")
    void emptyPeriod() {
        FinancialSummary s = FinancialSummary.empty(D1, D1);

        assertThat(s.income()).isEqualByComparingTo("0");
        assertThat(s.expenses()).isEqualByComparingTo("0");
        assertThat(s.payout()).isEqualByComparingTo("0");
        assertThat(s.reconciles()).isTrue();
    }

    @Test
    @DisplayName("Незаполненные денежные поля считаются нулём, а не ломают расчёт")
    void nullAmountsAreTreatedAsZero() {
        List<ProductFact> products = List.of(
                new ProductFact(D1, "u-1", 1L, 100L, 1, null, null, null, null));
        List<FeeFact> fees = List.of(
                new FeeFact(D1, "u-1", 1L, 100L, 6, null, FeeFact.FeeKind.DELIVERY));

        FinancialSummary s = FinancialModel.summarize(
                D1, D1, products, fees, Map.of());

        assertThat(s.income()).isEqualByComparingTo("0");
        assertThat(s.expenses()).isEqualByComparingTo("0");
        assertThat(s.reconciles()).isTrue();
    }

    @Test
    @DisplayName("Сложение периодов даёт сумму по дням")
    void plusCombinesPeriods() {
        FinancialSummary d1 = summarize(DAY_2026_04_10, D1, D1_PAYOUT);
        FinancialSummary d2 = summarize(DAY_2026_09_26, D2, D2_PAYOUT);

        FinancialSummary total = d1.plus(d2);

        assertThat(total.dateFrom()).isEqualTo(D1);
        assertThat(total.dateTo()).isEqualTo(D2);
        assertThat(total.income()).isEqualByComparingTo("56445.00");
        assertThat(total.expenses()).isEqualByComparingTo("36529.80");
        assertThat(total.payout()).isEqualByComparingTo("19915.20");
        assertThat(total.reconciles()).isTrue();
    }

    // ---------------------------------------------------------------- helpers

    private static FinancialSummary summarize(String fixture, LocalDate date, String payout) {
        return FinancialModel.summarize(
                date, date,
                productsFrom(fixture, date),
                feesFrom(fixture, date),
                Map.of(date, new BigDecimal(payout)));
    }

    /** Превращает сохранённый ответ OZON в факты ровно так же, как это делает запрос к БД. */
    private static List<ProductFact> productsFrom(String fixture, LocalDate date) {
        List<ProductFact> result = new ArrayList<>();
        for (var accrual : OzonTestFixturesAccess.parse(fixture)) {
            if (accrual.posting() == null) {
                continue;
            }
            for (var product : accrual.posting().products()) {
                var commission = product.commission();
                result.add(new ProductFact(date, accrual.unitNumber(), accrual.externalId(),
                        product.sku(), product.quantity(),
                        commission == null ? null : commission.salePrice(),
                        commission == null ? null : commission.saleCommission(),
                        commission == null ? null : commission.bonus(),
                        commission == null ? null : commission.coinvestment()));
            }
        }
        return result;
    }

    private static List<FeeFact> feesFrom(String fixture, LocalDate date) {
        List<FeeFact> result = new ArrayList<>();
        for (var accrual : OzonTestFixturesAccess.parse(fixture)) {
            if (accrual.posting() != null) {
                for (var product : accrual.posting().products()) {
                    for (var service : product.deliveryServices()) {
                        result.add(new FeeFact(date, accrual.unitNumber(), accrual.externalId(),
                                product.sku(), service.typeId(), service.amount(),
                                FeeFact.FeeKind.DELIVERY));
                    }
                }
            }
            if (accrual.itemFees() != null) {
                for (var itemFee : accrual.itemFees()) {
                    for (var detail : itemFee.fees()) {
                        result.add(new FeeFact(date, accrual.unitNumber(), accrual.externalId(),
                                itemFee.sku(), detail.typeId(), detail.amount(),
                                FeeFact.FeeKind.ITEM));
                    }
                }
            }
            if (accrual.nonItemFee() != null) {
                result.add(new FeeFact(date, accrual.unitNumber(), accrual.externalId(),
                        null, accrual.nonItemFee().typeId(), accrual.nonItemFee().amount(),
                        FeeFact.FeeKind.NON_ITEM));
            }
            if (accrual.containerFees() != null) {
                for (var fee : accrual.containerFees()) {
                    result.add(new FeeFact(date, accrual.unitNumber(), accrual.externalId(),
                            null, fee.typeId(), fee.amount(), FeeFact.FeeKind.CONTAINER));
                }
            }
        }
        return result;
    }
}
