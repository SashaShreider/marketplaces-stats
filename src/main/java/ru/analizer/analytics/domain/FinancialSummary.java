package ru.analizer.analytics.domain;

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
        BigDecimal payout,
        // Количество единиц. Держится рядом с деньгами, а не отдельно, потому что
        // «продажа» здесь одна и та же операция: разойтись они могут только в
        // расчёте, и заметнее всего это в тестах на сверке.
        //
        // Поля неотрицательные: возвраты не вычитаются из проданных, они стоят
        // рядом. Иначе «возвращено −1» читалось бы как отрицательное количество.
        //
        // В счёт не идут строки без sale_price — списания и удержания (штрафы за
        // доставку, корректировки). Их quantity равен единице, но продажей они не
        // являются, и включение дало бы завышение примерно на двадцать процентов.
        int soldQuantity,
        int returnedQuantity
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
     * Единиц продано за вычетом возвращённых.
     *
     * <p>Возвраты не вычитаются здесь намеренно: их видно отдельным числом, и
     * складывать два показателя в один — дело вызывающего. Молча уменьшенное число
     * выглядело бы как «продали 554», хотя продали 555 и вернули одну.
     */
    public int netQuantity() {
        return soldQuantity - returnedQuantity;
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

    @com.fasterxml.jackson.annotation.JsonProperty
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
                        : (other.dateFrom() == null || dateFrom.isBefore(other.dateFrom()))
                                ? dateFrom : other.dateFrom(),
                dateTo == null ? other.dateTo()
                        : (other.dateTo() == null || dateTo.isAfter(other.dateTo())) ? dateTo : other.dateTo(),
                n(sales).add(n(other.sales)),
                n(returns).add(n(other.returns)),
                n(partnerProgramme).add(n(other.partnerProgramme)),
                n(commission).add(n(other.commission)),
                n(logistics).add(n(other.logistics)),
                n(otherExpenses).add(n(other.otherExpenses)),
                n(payout).add(n(other.payout)),
                soldQuantity + other.soldQuantity,
                returnedQuantity + other.returnedQuantity);
    }

public static FinancialSummary empty(LocalDate from, LocalDate to) {
        return new FinancialSummary(from, to, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
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
