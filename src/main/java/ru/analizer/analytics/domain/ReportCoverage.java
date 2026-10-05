package ru.analizer.analytics.domain;

import ru.analizer.sync.domain.PeriodCoverage;

import java.time.LocalDate;
import java.util.List;

/**
 * Покрытие периода данными: что реально есть в базе, а что нет.
 *
 * @param provisionalDays дни, загруженные, но ещё не окончательные: начисления за них могут
 *                        прийти позже, поэтому суммы могут измениться
 */
public record ReportCoverage(
        int requestedDays,
        int loadedDays,
        int finalDays,
        int failedDays,
        int percentLoaded,
        List<LocalDate> missingDays,
        List<LocalDate> provisionalDays,
        List<LocalDate> failedDates
) {

    /** Период закрыт полностью и по всем дням данные окончательные. */
    @com.fasterxml.jackson.annotation.JsonProperty
    public boolean fullyFinal() {
        return loadedDays == requestedDays && failedDays == 0 && finalDays == requestedDays;
    }

    /** Есть ли смысл предлагать догрузку. */
    @com.fasterxml.jackson.annotation.JsonProperty
    public boolean needsSync() {
        return failedDays > 0 || loadedDays < requestedDays;
    }

    /**
     * Годится ли период для расчёта прибыли.
     *
     * <p>Прибыль сейчас не считается: в отчётах есть доходы, расходы и выплаты, но не
     * их разность. Метод оставлен, чтобы признак «данные окончательные» был посчитан
     * уже сейчас, а не пришлось бы додумывать его позже — когда период окончателен,
     * прибыль можно считать сразу, не дожидаясь обновления начислений.
     */
    @com.fasterxml.jackson.annotation.JsonProperty
    public boolean suitableForProfit() {
        return fullyFinal();
    }

    public static ReportCoverage from(PeriodCoverage coverage) {
        return new ReportCoverage(coverage.requestedDays(), coverage.loadedDays(),
                coverage.finalDays(), coverage.failedDays(), coverage.percentLoaded(),
                coverage.missingDays(), coverage.provisionalDays(), coverage.failedDates());
    }
}