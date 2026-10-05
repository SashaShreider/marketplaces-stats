package ru.analizer.analytics;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import ru.analizer.account.domain.Marketplace;

/**
 * Отчёт за период.
 *
 * <p>Три основные величины — доходы, расходы и к выплате — компоненты записи, а не
 * вычисляемые методы: Jackson сериализует только компоненты, и frontend получил бы
 * отчёт без итогов.
 *
 * <p>Рядом всегда идут {@link ReportStatus} и {@link ReportCoverage}: без них отчёт
 * выглядит одинаково для «день без начислений» и «день не загружен», и нули в нём
 * читаются как «денег не было».
 */
public record DailyReport(
        String marketplace,
        LocalDate dateFrom,
        LocalDate dateTo,
        ReportStatus status,
        ReportCoverage coverage,
        List<DailyRow> days,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal payout,
        FinancialSummary total,
        boolean reconciled
) {

    /** Можно ли доверять цифрам как окончательным. */
    @com.fasterxml.jackson.annotation.JsonProperty
    public boolean isFinal() {
        return coverage.fullyFinal();
    }
}