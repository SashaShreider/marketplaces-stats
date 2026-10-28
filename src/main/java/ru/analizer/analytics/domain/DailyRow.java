package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Строка отчёта за один день.
 *
 * @param soldQuantity     единиц продано. Возвраты сюда не входят, они считаются отдельно
 * @param returnedQuantity единиц возвращено. Поле рядом с проданными, а не вычтенное из
 *                         них: уменьшенное число выглядело бы как «продали 554», хотя
 *                         продали 555 и вернули одну
 * @param expensesByType   прочие расходы, разложенные по типам начислений. Отдаётся вместе
 *                         с итогом, чтобы подробный отчёт собрать позже, не переделывая
 *                         ни модель, ни API
 * @param expenseBreakdown расходы дня по статьям: комиссия, продвижение, логистика,
 *                         хранение, эквайринг, прочие. В отличие от {@code expensesByType}
 *                         покрывает расходы целиком и годится для карточки расходов
 */
public record DailyRow(
        LocalDate date,
        BigDecimal income,
        BigDecimal expenses,
        /** К выплате; {@code null}, если отчёт ограничен отбором — долю не выделить. */
        @Schema(nullable = true) BigDecimal payout,
        int soldQuantity,
        int returnedQuantity,
        Breakdown breakdown,
        List<FinancialSummary.TypeAmount> expensesByType,
        ExpenseBreakdown expenseBreakdown
) {

    public static DailyRow of(FinancialSummary day,
                              FinancialSummary.ExpenseByType byType,
                              ExpenseBreakdown breakdown) {
        // Выплата может быть неизвестна: при отборе по товарам OZON не отдаёт её долю.
        // Тогда она остаётся null, а не превращается в ноль — ноль был бы утверждением.
        return new DailyRow(day.dateFrom(), day.income(), day.expenses(), day.payout(),
                day.soldQuantity(), day.returnedQuantity(),
                new Breakdown(day.sales(), day.returns(), day.partnerProgramme(),
                        day.commission(), day.logistics(), day.otherExpenses()),
                byType.items(), breakdown);
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
