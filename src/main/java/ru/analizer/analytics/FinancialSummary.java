package ru.analizer.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Полная раскладка финансовой модели по одному периоду.
 *
 * <p>Значения — в рублях, со знаком как в OZON: доход положителен, расход отрицателен.
 * Тождество, проверенное на реальных выгрузках:
 *
 * <pre>
 * доходы + расходы = к выплате
 * </pre>
 *
 * <p>Держим его и при возвратах, где отрицательная цена продажи уменьшает доход,
 * а комиссия возвращается и уменьшает расход.
 */
public record FinancialSummary(
        LocalDate dateFrom,
        LocalDate dateTo,
        BigDecimal sales,
        BigDecimal returns,
        BigDecimal partnerProgramme,
        BigDecimal commission,
        BigDecimal logistics,
        BigDecimal otherExpenses,
        BigDecimal payout
) {

    /**
     * Доходы: цена продаж (за вычетом возвратов), начисления по программе партнёров
     * (бонусы) и ковейст.
     */
    public BigDecimal income() {
        return n(sales).add(n(returns)).add(n(partnerProgramme));
    }

    /**
     * Расходы комиссии, логистики и прочих списаний — положительным числом.
     */
    public BigDecimal expenses() {
        return n(commission).abs().add(n(logistics).abs()).add(n(otherExpenses).abs());
    }

    /**
     * К выплате берём из данных OZON, а не пересчитываем: так отчёт не расходится
     * с личным кабинетом, даже если OZON добавит новый вид начисления.
     */
    public BigDecimal payoutValue() {
        return n(payout);
    }

    /**
     * Расхождение между пересчитанным итогом и данными OZON. Должно быть нулём:
     * ненулевое значение означало бы, что мы не учли какой-то вид начисления.
     */
    public BigDecimal reconciliationDiff() {
        return income().subtract(expenses()).subtract(payoutValue());
    }

    public boolean reconciles() {
        return reconciliationDiff().signum() == 0;
    }

    private static BigDecimal n(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * Складывает два периода. Даты объединены так, чтобы отчёт оставался связным.
     */
    public FinancialSummary plus(FinancialSummary other) {
        return new FinancialSummary(
                dateFrom == null ? other.dateFrom()
                        : (other.dateFrom() == null || dateFrom.isBefore(other.dateFrom())
                                ? dateFrom : other.dateFrom()),
                dateTo == null ? other.dateTo()
                        : (other.dateTo() == null || dateTo.isAfter(other.dateTo()) ? dateTo : other.dateTo()),
                n(sales).add(n(other.sales)),
                n(returns).add(n(other.returns)),
                n(partnerProgramme).add(n(other.partnerProgramme)),
                n(commission).add(n(other.commission)),
                n(logistics).add(n(other.logistics)),
                n(otherExpenses).add(n(other.otherExpenses)),
                n(payout).add(n(other.payout)));
    }

    public static FinancialSummary empty(LocalDate from, LocalDate to) {
        return new FinancialSummary(from, to, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * Разбивка прочих расходов по типам начислений — для детализации отчёта.
     * Суммы отрицательные, как их отдаёт OZON.
     */
    public record ExpenseByType(List<TypeAmount> items) {
    }

    public record TypeAmount(Integer typeId, String name, BigDecimal amount) {
    }
}
