package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import ru.analizer.integration.ozon.dto.finance.Commission;

/**
 * Строка отчёта за один день.
 *
 * @param expensesByType прочие расходы, разложенные по типам начислений. Отдаётся вместе
 *                       с итогом, чтобы подробный отчёт собрать позже, не переделывая
 *                       ни модель, ни API
 */
public record DailyRow(
        LocalDate date,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal payout,
        Breakdown breakdown,
        List<FinancialSummary.TypeAmount> expensesByType
) {

    public static DailyRow of(FinancialSummary day, FinancialSummary.ExpenseByType byType) {
        return new DailyRow(day.dateFrom(), day.income(), day.expenses(), day.payoutValue(),
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