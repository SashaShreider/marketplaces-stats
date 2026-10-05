package ru.analizer.sync;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.analytics.ReportCoverage;
import ru.analizer.analytics.DailyAnalyticsService;
import ru.analizer.analytics.ReportStatus;
import ru.analizer.sync.domain.DayStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import ru.analizer.sync.domain.ImportedDay;
import ru.analizer.sync.domain.PeriodCoverage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Правила зрелости данных и покрытия периода.
 *
 * <p>Проверяются на самой сущности {@code ImportedDay}: репозиторий и Spring здесь не нужны,
 * потому что вся логика зрелости — в самом объекте. Так тест остаётся быстрым и
 * не подменяет часы по умолчанию.
 */
class DayStateFinalityTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final int MATURITY_DAYS = 3;

    private ru.analizer.sync.domain.ImportedDay day(LocalDate date) {
        ru.analizer.sync.domain.ImportedDay syncDay = new ru.analizer.sync.domain.ImportedDay(null, date);
        syncDay.refreshFinality(TODAY, MATURITY_DAYS, true);
        return syncDay;
    }

    @Test
    @DisplayName("День внутри окна зрелости не окончательный")
    void recentDayIsNotFinal() {
        assertThat(day(TODAY).isFinalDay()).as("сегодня").isFalse();
        assertThat(day(TODAY.minusDays(1)).isFinalDay()).as("вчера").isFalse();
        assertThat(day(TODAY.minusDays(2)).isFinalDay()).as("позавчера").isFalse();
    }

    @Test
    @DisplayName("День за пределами окна зрелости окончательный")
    void dayOutsideWindowIsFinal() {
        assertThat(day(TODAY.minusDays(3)).isFinalDay()).as("3 дня назад — граница окна").isTrue();
        assertThat(day(TODAY.minusDays(10)).isFinalDay()).isTrue();
    }

    @Test
    @DisplayName("Окно зрелости в 3 дня: граница ровно на 3-м дне назад")
    void maturityWindowBoundary() {
        ru.analizer.sync.domain.ImportedDay boundary = new ru.analizer.sync.domain.ImportedDay(null, TODAY.minusDays(3));
        boundary.refreshFinality(TODAY, 3, true);
        ru.analizer.sync.domain.ImportedDay justInside = new ru.analizer.sync.domain.ImportedDay(null, TODAY.minusDays(2));
        justInside.refreshFinality(TODAY, 3, true);

        assertThat(boundary.isFinalDay()).isTrue();
        assertThat(justInside.isFinalDay()).isFalse();
    }

    @Test
    @DisplayName("Недозагруженный день не бывает окончательным даже если старый")
    void failedDayIsNeverFinal() {
        ru.analizer.sync.domain.ImportedDay record =
                new ru.analizer.sync.domain.ImportedDay(null, TODAY.minusDays(30));
        record.markFailed("OZON вернул 500", Instant.now());
        record.refreshFinality(TODAY, 3, record.dataProvenStable(3, TODAY));

        assertThat(record.getStatus()).isEqualTo(DayStatus.FAILED);
        assertThat(record.isFinalDay()).isFalse();
        assertThat(record.getLastError()).isEqualTo("OZON вернул 500");
    }

    @Test
    @DisplayName("Повторная загрузка без изменений не увеличивает счётчик изменений")
    void unchangedResyncDoesNotCountAsChange() {
        ru.analizer.sync.domain.ImportedDay record =
                new ru.analizer.sync.domain.ImportedDay(null, TODAY.minusDays(1));
        Instant now = Instant.now();

        assertThat(record.recordSyncResult(new BigDecimal("100"), 5, now)).as("первая загрузка").isFalse();
        assertThat(record.recordSyncResult(new BigDecimal("100"), 5, now)).as("повтор без изменений").isFalse();
        assertThat(record.getChangeCount()).isZero();
        assertThat(record.getUnchangedSince()).as("данные признаны стабильными").isNotNull();
    }

    @Test
    @DisplayName("Повторная загрузка с изменившейся суммой фиксирует изменение")
    void changedResyncIsCounted() {
        ru.analizer.sync.domain.ImportedDay record =
                new ru.analizer.sync.domain.ImportedDay(null, TODAY.minusDays(1));
        Instant now = Instant.now();

        record.recordSyncResult(new BigDecimal("100"), 5, now);
        // Вечером пришло ещё одно начисление — сумма выросла.
        assertThat(record.recordSyncResult(new BigDecimal("120"), 6, now)).isTrue();

        assertThat(record.getChangeCount()).isEqualTo(1);
        assertThat(record.getUnchangedSince()).as("изменение обнуляет стабильность").isNull();
    }

    @Test
    @DisplayName("Изменение количества операций тоже считается изменением")
    void changedCountIsDetectedEvenIfAmountIsEqual() {
        ru.analizer.sync.domain.ImportedDay record =
                new ru.analizer.sync.domain.ImportedDay(null, TODAY.minusDays(1));
        Instant now = Instant.now();

        record.recordSyncResult(new BigDecimal("100"), 5, now);
        // Сумма случайно совпала, но операций стало больше: возвраты могут компенсироваться.
        assertThat(record.recordSyncResult(new BigDecimal("100.00"), 7, now)).isTrue();
    }

    @Test
    @DisplayName("Данные признаются стабильными только после успешной загрузки")
    void stabilityRequiresSuccess() {
        ru.analizer.sync.domain.ImportedDay record =
                new ru.analizer.sync.domain.ImportedDay(null, TODAY.minusDays(1));
        assertThat(record.dataProvenStable(3, TODAY)).as("до загрузки").isFalse();

        record.markFailed("сеть", Instant.now());
        assertThat(record.dataProvenStable(3, TODAY)).as("после ошибки").isFalse();

        record.recordSyncResult(new BigDecimal("10"), 1, Instant.now());
        assertThat(record.dataProvenStable(3, TODAY)).as("после успеха").isTrue();
    }

    @Test
    @DisplayName("Очень старый день считается стабильным без повторных проверок")
    void veryOldDayIsStableByAge() {
        ru.analizer.sync.domain.ImportedDay record =
                new ru.analizer.sync.domain.ImportedDay(null, TODAY.minusYears(1));
        record.recordSyncResult(new BigDecimal("10"), 1, Instant.now());

        assertThat(record.dataProvenStable(3, TODAY))
                .as("год назад OZON уже не изменит")
                .isTrue();
    }

    @Test
    @DisplayName("Покрытие: пустой период, полный и частичный считаются по-разному")
    void coverageArithmetic() {
        PeriodCoverage empty = new PeriodCoverage(30, 0, 0, 0,
                List.<LocalDate>of(), List.<LocalDate>of(), List.<LocalDate>of(), BigDecimal.ZERO);
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.complete()).isFalse();
        assertThat(empty.percentLoaded()).isZero();
        assertThat(empty.needsSync()).isTrue();

        PeriodCoverage full = new PeriodCoverage(30, 30, 28, 0,
                List.<LocalDate>of(), List.of(LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30)),
                List.of(), BigDecimal.TEN);
        assertThat(full.complete()).isTrue();
        assertThat(full.allFinal()).as("2 дня ещё не окончательные").isFalse();
        assertThat(full.percentLoaded()).isEqualTo(100);

        PeriodCoverage withErrors = new PeriodCoverage(30, 28, 28, 2,
                List.<LocalDate>of(), List.<LocalDate>of(),
                List.of(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 16)), BigDecimal.TEN);
        assertThat(withErrors.complete()).isFalse();
        assertThat(withErrors.percentLoaded()).isEqualTo(93);
    }

    @Test
    @DisplayName("Статус отчёта выводится из покрытия, а не угадывается")
    void reportStatusDerivedFromCoverage() {
        List<LocalDate> nothing = List.of();
        List<LocalDate> two = List.of(LocalDate.of(2026, 9, 15));

        assertThat(DailyAnalyticsService.statusOf(
                new PeriodCoverage(30, 30, 30, 0, List.<LocalDate>of(), List.<LocalDate>of(), List.<LocalDate>of(), BigDecimal.TEN)))
                .isEqualTo(ReportStatus.READY);

        assertThat(DailyAnalyticsService.statusOf(
                new PeriodCoverage(30, 28, 28, 0, List.<LocalDate>of(), List.<LocalDate>of(), List.<LocalDate>of(), BigDecimal.TEN)))
                .isEqualTo(ReportStatus.PARTIAL);

        assertThat(DailyAnalyticsService.statusOf(
                new PeriodCoverage(30, 0, 0, 0, List.<LocalDate>of(), List.<LocalDate>of(), List.<LocalDate>of(), BigDecimal.ZERO)))
                .isEqualTo(ReportStatus.NOT_LOADED);

        // Ошибка важнее полноты: пользователь должен увидеть именно её.
        assertThat(DailyAnalyticsService.statusOf(
                new PeriodCoverage(30, 28, 28, 2, List.<LocalDate>of(), List.<LocalDate>of(), two, BigDecimal.TEN)))
                .isEqualTo(ReportStatus.HAS_ERRORS);
    }

    @Test
    @DisplayName("Покрытие попадает в отчёт и говорит, нужна ли догрузка")
    void coverageFeedsReport() {
        ReportCoverage coverage = ReportCoverage.from(new PeriodCoverage(30, 28, 26, 0,
                List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2)),
                List.of(LocalDate.of(2026, 9, 29)),
                List.of(), BigDecimal.TEN));

        assertThat(coverage.loadedDays()).isEqualTo(28);
        assertThat(coverage.missingDays()).containsExactly(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2));
        assertThat(coverage.provisionalDays()).containsExactly(LocalDate.of(2026, 9, 29));
        assertThat(coverage.needsSync()).isTrue();
        assertThat(coverage.fullyFinal()).isFalse();
    }

    @Test
    @DisplayName("Дни, которых нет, отделяются от дней без начислений")
    void missingDaysAreDistinctFromEmptyDays() {
        // День без начислений — загружен, сумма ноль. Отличаем от «не загружен».
        ru.analizer.sync.domain.ImportedDay emptyButLoaded =
                new ru.analizer.sync.domain.ImportedDay(null, TODAY.minusDays(5));
        emptyButLoaded.recordSyncResult(BigDecimal.ZERO, 0, Instant.now());
        emptyButLoaded.refreshFinality(TODAY, 3, emptyButLoaded.dataProvenStable(3, TODAY));

        assertThat(emptyButLoaded.getStatus()).isEqualTo(DayStatus.DONE);
        assertThat(emptyButLoaded.getAccrualCount()).isZero();
        assertThat(emptyButLoaded.getTotalAmount()).isEqualByComparingTo("0");
        assertThat(emptyButLoaded.isFinalDay()).as("загруженный пустой день окончателен").isTrue();
    }
}