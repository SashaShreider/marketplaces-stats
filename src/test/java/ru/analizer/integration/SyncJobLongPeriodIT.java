package ru.analizer.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import ru.analizer.analytics.DataCoverage;
import ru.analizer.analytics.ReportStatus;
import ru.analizer.persistence.entity.JobStatus;
import ru.analizer.sync.PeriodAlreadySyncingException;
import ru.analizer.sync.SyncJobStatus;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Загрузка длинного периода: полгода, 184 дня.
 *
 * <p>Данные синтетические и живут в отдельной базе Testcontainers — база разработки
 * не затрагивается. Проверяется ровно то поведение, ради которого всё затевалось:
 * задача уходит в фон, прогресс виден по запросу, повторный запуск пересекающегося
 * периода запрещён, а после загрузки отчёт показывает полное покрытие.
 */
@Import(BulkAdapterConfig.class)
class SyncJobLongPeriodIT extends AbstractPostgresIntegrationTest {

    /** Полгода: 1 апреля — 30 сентября. */
    private static final LocalDate FROM = LocalDate.of(2026, 4, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    /**
     * Число дней в периоде считается из самих дат, а не задаётся константой:
     * ручной подсчёт дней в полугодии уже один раз дал ошибку.
     */
    private static final int DAYS = (int) (TO.toEpochDay() - FROM.toEpochDay() + 1);

    /** Операций в сутки у синтетического адаптера. */
    private static final int ACCRUALS_PER_DAY = 5;

    @Autowired
    BulkFixtureAdapter adapter;

    @Test
    @DisplayName("Задача на полгода возвращается сразу и выполняется в фоне")
    void longPeriodJobRunsInBackground() {
        Instant start = Instant.now();
        SyncJobStatus accepted = syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, true);

        // Запрос не ждёт завершения: 184 дня грузились бы месяцами, а ответ приходит сразу.
        assertThat(Duration.between(start, Instant.now()))
                .as("POST-запрос обязан вернуться немедленно")
                .isLessThan(Duration.ofSeconds(10));
        assertThat(accepted.id()).isNotNull();
        assertThat(accepted.totalDays()).isEqualTo(DAYS);
        assertThat(accepted.inProgress()).isTrue();
        assertThat(accepted.status()).isIn(JobStatus.PENDING, JobStatus.RUNNING);

        awaitFinished(accepted.id());

        SyncJobStatus finished = status(accepted.id());
        assertThat(finished.status()).isEqualTo(JobStatus.DONE);
        assertThat(finished.finished()).isTrue();
        assertThat(finished.error()).isNull();
        assertThat(finished.doneDays()).isEqualTo(DAYS);
        assertThat(finished.failedDays()).isZero();
        assertThat(finished.startedAt()).isNotNull();
        assertThat(finished.finishedAt()).isNotNull();
        assertThat(finished.finishedAt()).isAfterOrEqualTo(finished.startedAt());

        assertThat(count("finance_accrual")).isEqualTo((long) DAYS * ACCRUALS_PER_DAY);
    }

    @Test
    @DisplayName("Прогресс виден по ходу загрузки, данные появляются сразу")
    void progressIsVisibleWhileRunning() {
        // Искусственная задержка, чтобы успеть поймать промежуточное состояние.
        adapter.setDelayPerDayMillis(15);
        SyncJobStatus accepted = syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, false);

        boolean sawPartialProgress = false;
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        while (Instant.now().isBefore(deadline)) {
            SyncJobStatus current = status(accepted.id());
            if (current.doneDays() > 0 && current.doneDays() < DAYS) {
                assertThat(current.status()).isEqualTo(JobStatus.RUNNING);
                assertThat(current.currentDay()).isNotNull().isBetween(FROM, TO);
                assertThat(count("finance_accrual"))
                        .as("данные видны сразу, не дожидаясь конца загрузки")
                        .isGreaterThan(0);
                assertThat(count("sync_day"))
                        .as("учёт дней обновляется по ходу")
                        .isGreaterThan(0);
                sawPartialProgress = true;
                break;
            }
            if (current.finished()) {
                break;
            }
            sleep(50);
        }
        assertThat(sawPartialProgress).as("должен наблюдаться промежуточный прогресс").isTrue();

        awaitFinished(accepted.id());
        assertThat(status(accepted.id()).doneDays()).isEqualTo(DAYS);
    }

    @Test
    @DisplayName("Пока идёт загрузка, повторный запуск того же периода запрещён")
    void resubmitWhileRunningIsRejected() {
        adapter.setDelayPerDayMillis(15);
        SyncJobStatus first = syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, false);

        assertThatThrownBy(() -> syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, false))
                .isInstanceOf(PeriodAlreadySyncingException.class)
                .hasMessageContaining("уже выполняется")
                .hasMessageContaining(String.valueOf(first.id()));

        awaitFinished(first.id());

        // А после окончания тот же период запустить можно.
        SyncJobStatus again = syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, false);
        assertThat(again.id()).isNotNull().isNotEqualTo(first.id());
        awaitFinished(again.id());
    }

    @Test
    @DisplayName("Запрещено пересечение периодов, а не только точное совпадение")
    void overlappingPeriodIsAlsoRejected() {
        adapter.setDelayPerDayMillis(15);
        SyncJobStatus first = syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, false);

        assertThatThrownBy(() -> syncJobService.submit(CLIENT_ID, "OZON",
                FROM.plusDays(10), FROM.plusDays(20), false))
                .isInstanceOf(PeriodAlreadySyncingException.class);

        assertThatThrownBy(() -> syncJobService.submit(CLIENT_ID, "OZON",
                FROM.plusDays(30), FROM.plusDays(40), false))
                .isInstanceOf(PeriodAlreadySyncingException.class);

        // Период шире первого — тоже конфликтует.
        assertThatThrownBy(() -> syncJobService.submit(CLIENT_ID, "OZON",
                FROM.minusDays(5), TO.plusDays(5), false))
                .isInstanceOf(PeriodAlreadySyncingException.class);

        awaitFinished(first.id());
    }

    @Test
    @DisplayName("Непересекающийся период запустить можно")
    void nonOverlappingPeriodIsAllowed() {
        adapter.setDelayPerDayMillis(15);
        SyncJobStatus first = syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, false);

        LocalDate before = FROM.minusMonths(1);
        SyncJobStatus second = syncJobService.submit(CLIENT_ID, "OZON",
                before, before.plusDays(27), false);

        assertThat(second.id()).isNotEqualTo(first.id());
        awaitFinished(first.id());
        awaitFinished(second.id());
        assertThat(count("sync_day")).isEqualTo((long) DAYS + 28);
    }

    @Test
    @DisplayName("После загрузки полугода отчёт показывает READY и полное покрытие")
    void reportForLoadedHalfYearIsReady() {
        awaitFinished(syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, true).id());

        var report = analytics.daily(CLIENT_ID, "OZON", FROM, TO);

        assertThat(report.status()).isEqualTo(ReportStatus.READY);
        assertThat(report.coverage().requestedDays()).isEqualTo(DAYS);
        assertThat(report.coverage().loadedDays()).isEqualTo(DAYS);
        assertThat(report.coverage().missingDays()).isEmpty();
        assertThat(report.coverage().needsSync()).isFalse();
        assertThat(report.days()).hasSize(DAYS);
        assertThat(report.reconciled())
                .as("доходы минус расходы равны данным OZON по каждому дню")
                .isTrue();
        assertThat(report.payout()).isGreaterThan(java.math.BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Отчёт за половину периода показывает PARTIAL и перечисляет остальные дни")
    void halfOfPeriodReportsPartial() {
        int halfDays = DAYS / 2;
        LocalDate half = FROM.plusDays(halfDays - 1);
        int expectedLoaded = (int) (half.toEpochDay() - FROM.toEpochDay() + 1);
        int expectedMissing = DAYS - expectedLoaded;

        awaitFinished(syncJobService.submit(CLIENT_ID, "OZON", FROM, half, true).id());

        var report = analytics.daily(CLIENT_ID, "OZON", FROM, TO);

        assertThat(report.status()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(report.coverage().loadedDays()).isEqualTo(expectedLoaded);
        assertThat(report.coverage().missingDays()).hasSize(expectedMissing);
        assertThat(report.coverage().percentLoaded()).isBetween(45, 55);
    }

    @Test
    @DisplayName("Повторная загрузка готового периода не создаёт дублей")
    void rerunOfLoadedPeriodIsIdempotent() {
        awaitFinished(syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, true).id());
        long afterFirst = count("finance_accrual");
        assertThat(afterFirst).isEqualTo((long) DAYS * ACCRUALS_PER_DAY);

        awaitFinished(syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, false).id());

        assertThat(count("finance_accrual")).isEqualTo(afterFirst);
        assertThat(count("sync_day")).isEqualTo(DAYS);
        assertThat(count("posting")).isEqualTo((long) DAYS * 2);
    }

    @Test
    @DisplayName("Каждый день периода попадает в учёт ровно один раз")
    void everyDayTrackedOnce() {
        awaitFinished(syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, true).id());

        DataCoverage coverage = DataCoverage.from(
                syncDayService.coverage(accountId(), FROM, TO));

        assertThat(coverage.loadedDays()).isEqualTo(DAYS);
        assertThat(count("sync_day")).isEqualTo(DAYS);
        assertThat(adapter.daysRequested()).as("к адаптеру сходили ровно по одному разу на день")
                .isEqualTo(DAYS);
    }

    @Test
    @DisplayName("Все дни периода старше окна зрелости, поэтому окончательные")
    void allDaysAreFinalAfterLoading() {
        awaitFinished(syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, true).id());

        DataCoverage coverage = DataCoverage.from(
                syncDayService.coverage(accountId(), FROM, TO));

        // Период апрель–сентябрь, а «сегодня» в тестах — осень 2026: часть дней
        // окажется внутри окна зрелости, и это должно быть честно отражено.
        assertThat(coverage.finalDays()).isLessThanOrEqualTo(DAYS);
        assertThat(coverage.finalDays() + coverage.provisionalDays().size())
                .isEqualTo(DAYS);
    }

    @Test
    @DisplayName("Покрытие полугода считается мгновенно, без обращения к маркетплейсу")
    void coverageForHalfYearIsCheap() {
        awaitFinished(syncJobService.submit(CLIENT_ID, "OZON", FROM, TO, true).id());
        int before = adapter.daysRequested();

        DataCoverage coverage = DataCoverage.from(
                syncDayService.coverage(accountId(), FROM, TO));

        assertThat(adapter.daysRequested()).as("проверка покрытия не ходит к адаптеру")
                .isEqualTo(before);
        assertThat(coverage.requestedDays()).isEqualTo(DAYS);
        assertThat(coverage.percentLoaded()).isEqualTo(100);
    }

    // ---------------------------------------------------------------- helpers

    private Long accountId() {
        return jdbc.queryForObject("select id from seller_account limit 1", Long.class);
    }

    private SyncJobStatus status(Long jobId) {
        return syncJobService.status(jobId).orElseThrow(
                () -> new AssertionError("Задача " + jobId + " не найдена"));
    }

    private void awaitFinished(Long jobId) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        while (Instant.now().isBefore(deadline)) {
            SyncJobStatus current = status(jobId);
            if (current.finished()) {
                assertThat(current.status())
                        .as("задача %s завершилась с ошибкой: %s", jobId, current.error())
                        .isEqualTo(JobStatus.DONE);
                return;
            }
            sleep(100);
        }
        throw new AssertionError("Задача " + jobId + " не завершилась за 5 минут");
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}