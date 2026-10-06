package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Строка отчёта за один день.
 *
 * @param soldQuantity    единиц продано. Возвраты сюда не входят, они считаются отдельно
 * @param returnedQuantity единиц возвращено. Поле рядом с проданными, а не вычтенное из
 *                        них: уменьшенное число выглядело бы как «продали 554», хотя
 *                        продали 555 и вернули одну
 * @param expensesByType прочие расходы, разложенные по типам начислений. Отдаётся вместе
 *                       с итогом, чтобы подробный отчёт собрать позже, не переделывая
 *                       ни модель, ни API
 */
public record DailyRow(
        LocalDate date,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal payout,
        int soldQuantity,
        int returnedQuantity,
        Breakdown breakdown,
        List<FinancialSummary.TypeAmount> expensesByType
) {

    public static DailyRow of(FinancialSummary day, FinancialSummary.ExpenseByType byType) {
        return new DailyRow(day.dateFrom(), day.income(), day.expenses(), day.payoutValue(),
                day.soldQuantity(), day.returnedQuantity(),
                new Breakdown(day.sales(), day.returns(), day.partnerProgramme(),
                        day.commission(), day.logistics(), day.otherExpenses()),
                byType.items());
    }

    /**
     * Детализация показателей дня.
     *
     * <p>Доход складывается из продаж (за вычетом возвратов) и начислений по программе
     * партнёров. Последние — доход, а не расход, и без них «к выплате» разошёлся бы
     * ровно на их сумму.
     */
    public record Breakdown(
            BigDecimal sales,
            BigDecimal returns,
            BigDecimal partnerProgramme,
            BigDecimal commission,
            BigDecimal logistics,
            BigDecimal otherExpenses
    ) {
    }
}