package ru.analizer.integration;

import org.springframework.context.annotation.Import;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.account.domain.Marketplace;
import ru.analizer.analytics.ReportCoverage;
import ru.analizer.analytics.ReportStatus;
import ru.analizer.sync.application.AccrualImportService;
import ru.analizer.sync.application.DayStateService;
import ru.analizer.sync.domain.PeriodCoverage;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Главное поведение задачи: отчёт не должен показывать нули за день, который не загружен.
 *
 * <p>До появления {@code sync_day} отчёт за любой период возвращал нули, и «день без
 * начислений» было невозможно отличить от «день не забрали из OZON». Именно это и было
 * неправильное поведение, с которого началась работа.
 */
@Import(FixtureAdapterConfig.class)
class ReportHonestyIT extends AbstractPostgresIntegrationTest {

    private static final String DAY_2026_04_10 = "fixtures/accruals-2026-04-10.json";


    /**
     * Создаёт аккаунт синхронизацией одного дня: без него отчёт для любого периода
     * честно отвечает «аккаунт не найден», и проверить покрытие невозможно.
     */
    private void createAccount() {
        LocalDate anchor = LocalDate.of(2026, 4, 10);
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(anchor, DAY_2026_04_10);
        accrualImportService.importAccruals(accountId(), anchor, anchor);
    }

    @Test
    @DisplayName("В базе нет ни одного дня периода: отчёт говорит «данных нет», а не «денег нет»")
    void emptyDatabaseReportsNotLoaded() {
        createAccount();

        // Период, в котором действительно ничего не загружали.
        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3));

        assertThat(report.status()).isEqualTo(ReportStatus.NOT_LOADED);
        assertThat(report.coverage().loadedDays()).isZero();
        assertThat(report.coverage().requestedDays()).isEqualTo(3);
        assertThat(report.coverage().missingDays()).containsExactly(
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 3));
        assertThat(report.coverage().needsSync()).isTrue();
        // Нули в ответе остаются, но статус не даёт их принять за «денег не было».
        assertThat(report.payout()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Аккаунта вообще нет: отчёт честно говорит «данных нет»")
    void missingAccountReportsNotLoaded() {
        // Отсутствие аккаунта — это «ничего не загружено», а не сбой. Отчёт должен
        // сказать об этом прямо, иначе фронтенд не предложит загрузку.
        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 12));

        assertThat(report.status()).isEqualTo(ReportStatus.NOT_LOADED);
        assertThat(report.coverage().loadedDays()).isZero();
        assertThat(report.coverage().missingDays()).hasSize(3);
        assertThat(report.coverage().needsSync()).isTrue();

        // Покрытие отвечает и без аккаунта — иначе спросить «что есть» невозможно.
        PeriodCoverage coverage = dayStateService.coverage(null,
                LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 12));
        assertThat(coverage.loadedDays()).isZero();
        assertThat(coverage.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Частично загруженный период показывает PARTIAL и перечисляет недостающие дни")
    void partialPeriodTellsWhatIsMissing() {
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(LocalDate.of(2026, 4, 10), DAY_2026_04_10);
        accrualImportService.importAccruals(accountId(), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 10));

        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 13));

        assertThat(report.status()).isEqualTo(ReportStatus.PARTIAL);
        assertThat(report.coverage().loadedDays()).isEqualTo(1);
        assertThat(report.coverage().requestedDays()).isEqualTo(4);
        assertThat(report.coverage().missingDays())
                .containsExactly(LocalDate.of(2026, 4, 11), LocalDate.of(2026, 4, 12),
                        LocalDate.of(2026, 4, 13));
        assertThat(report.coverage().percentLoaded()).isEqualTo(25);

        // Загруженный день при этом посчитан полностью.
        assertThat(report.payout()).isEqualByComparingTo("11297.23");
    }

    @Test
    @DisplayName("Полностью загруженный период даёт READY")
    void fullyLoadedPeriodIsReady() {
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(LocalDate.of(2026, 4, 10), DAY_2026_04_10);
        accrualImportService.importAccruals(accountId(), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 10));

        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 10));

        assertThat(report.status()).isEqualTo(ReportStatus.READY);
        assertThat(report.coverage().missingDays()).isEmpty();
        assertThat(report.coverage().needsSync()).isFalse();
    }

    @Test
    @DisplayName("Свежий день не окончательный: отчёт это признаёт")
    void freshDayIsMarkedProvisional() {
        LocalDate today = LocalDate.now();
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(today, DAY_2026_04_10);
        accrualImportService.importAccruals(accountId(), today, today);

        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(), today, today);

        assertThat(report.status()).isEqualTo(ReportStatus.READY);
        assertThat(report.coverage().provisionalDays()).containsExactly(today);
        assertThat(report.coverage().finalDays()).isZero();
        assertThat(report.isFinal()).as("сегодняшние данные ещё могут уточниться").isFalse();
    }

    @Test
    @DisplayName("День без начислений не путается с незагруженным днём")
    void emptyLoadedDayIsNotTheSameAsMissingDay() {
        LocalDate oldDay = LocalDate.of(2026, 4, 10);
        FixtureAdapters.FIXTURES.clear();
        // Адаптер отдаёт пустой ответ: день загружен, начислений действительно нет.
        FixtureAdapters.FIXTURES.put(oldDay, "fixtures/empty-day.json");

        accrualImportService.importAccruals(accountId(), oldDay, oldDay);

        var coverage = dayStateService.coverage(accountId(), oldDay, oldDay);
        assertThat(coverage.loadedDays()).as("день загружен").isEqualTo(1);
        assertThat(coverage.missingDays()).as("но ничего не пропало").isEmpty();
        assertThat(coverage.finalDays()).as("старый день окончателен").isEqualTo(1);

        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(), oldDay, oldDay);
        assertThat(report.status()).isEqualTo(ReportStatus.READY);
        assertThat(report.payout()).isEqualByComparingTo("0");
        assertThat(report.coverage().needsSync()).isFalse();
    }

    @Test
    @DisplayName("Эндпоинт покрытия отвечает без обращения к OZON")
    void coverageEndpointAnswers() {
        LocalDate oldDay = LocalDate.of(2026, 4, 10);
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(oldDay, DAY_2026_04_10);
        accrualImportService.importAccruals(accountId(), oldDay, oldDay);

        PeriodCoverage coverage = dayStateService.coverage(accountId(),
                LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 20));

        assertThat(coverage.requestedDays()).isEqualTo(11);
        assertThat(coverage.loadedDays()).isEqualTo(1);
        assertThat(coverage.sumOfLoadedDays()).isEqualByComparingTo("11297.23");
        assertThat(coverage.needsSync()).isTrue();
    }

    @Test
    @DisplayName("Покрытие периода без данных в базе считается пустым, а не ошибочным")
    void coverageOfEmptyPeriod() {
        createAccount();

        PeriodCoverage coverage = dayStateService.coverage(accountId(),
                LocalDate.of(2030, 1, 1), LocalDate.of(2030, 1, 31));

        assertThat(coverage.requestedDays()).isEqualTo(31);
        assertThat(coverage.loadedDays()).isZero();
        assertThat(coverage.missingDays()).hasSize(31);
        assertThat(coverage.isEmpty()).isTrue();
        assertThat(coverage.complete()).isFalse();

        ReportCoverage mapped = ReportCoverage.from(coverage);
        assertThat(mapped.percentLoaded()).isZero();
        assertThat(mapped.needsSync()).isTrue();
    }
}