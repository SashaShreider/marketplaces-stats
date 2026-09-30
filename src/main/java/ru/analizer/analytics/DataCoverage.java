package ru.analizer.analytics;

import ru.analizer.sync.SyncCoverage;

import java.time.LocalDate;
import java.util.List;

/**
 * Покрытие периода данными: что реально есть в базе, а что нет.
 *
 * @param provisionalDays дни, загруженные, но ещё не окончательные: начисления за них могут
 *                        прийти позже, поэтому суммы могут измениться
 */
public record DataCoverage(
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
     * <p>Пока нет даже этого: прибыль не считается в MVP (SPEC.md, раздел «Ограничения»).
     * Метод оставлен, чтобы признак «данные окончательные» был посчитан уже сейчас,
     * а не пришлось бы додумывать его позже.
     */
    @com.fasterxml.jackson.annotation.JsonProperty
    public boolean suitableForProfit() {
        return fullyFinal();
    }

    public static DataCoverage from(SyncCoverage coverage) {
        return new DataCoverage(coverage.requestedDays(), coverage.loadedDays(),
                coverage.finalDays(), coverage.failedDays(), coverage.percentLoaded(),
                coverage.missingDays(), coverage.provisionalDays(), coverage.failedDates());
    }
}