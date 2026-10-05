package ru.analizer.analytics.integration;

import org.springframework.context.annotation.Import;

import org.junit.jupiter.api.DisplayName;
import ru.analizer.account.domain.Marketplace;
import ru.analizer.analytics.domain.DailyReport;
import ru.analizer.analytics.domain.ReportStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import ru.analizer.support.AbstractPostgresIntegrationTest;
import ru.analizer.support.FixtureAdapterConfig;
import ru.analizer.support.FixtureAdapters;
import ru.analizer.sync.application.AccrualImportService;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Ежедневная аналитика на настоящей базе: от сохранённых операций до показателей отчёта.
 *
 * <p>Ожидаемые значения подсчитаны вручную по двум реальным выгрузкам OZON,
 * поэтому проверяется не «посчиталось ли что-то», а «посчиталось ли то же, что у OZON».
 */
@Import(FixtureAdapterConfig.class)
class DailyAnalyticsIT extends AbstractPostgresIntegrationTest {

    private static final String DAY_2026_04_10 = "fixtures/accruals-2026-04-10.json";
    private static final String DAY_2026_09_26 = "fixtures/accruals-2026-09-26-full.json";

    private DailyReport syncAndReport(String fixture, LocalDate date) {
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(date, fixture);
        accrualImportService.refreshAccrualTypes(accountId());
        accrualImportService.importAccruals(accountId(), date, date);
        return analytics.dailyReport(MARKETPLACE, accountIdOpt(), date, date);
    }

    @Test
    @DisplayName("2026-04-10: доходы, расходы и к выплате сходятся с ручным подсчётом")
    void day2026_04_10() {
        var report = syncAndReport(DAY_2026_04_10, LocalDate.of(2026, 4, 10));

        assertThat(report.days()).hasSize(1);
        var row = report.days().getFirst();

        assertThat(row.income()).isEqualByComparingTo("31165.00");
        assertThat(row.expenses()).isEqualByComparingTo("19867.77");
        assertThat(row.payout()).isEqualByComparingTo("11297.23");
        assertThat(report.reconciled()).isTrue();

        // Детализация внутри ответа — чтобы подробный отчёт собрать позже,
        // не переделывая ни модель, ни API.
        assertThat(row.breakdown().sales()).isEqualByComparingTo("19804.67");
        assertThat(row.breakdown().returns()).isEqualByComparingTo("-1039.05");
        assertThat(row.breakdown().partnerProgramme()).isEqualByComparingTo("12399.38");
        assertThat(row.breakdown().commission()).isEqualByComparingTo("-12879.33");
        assertThat(row.breakdown().logistics()).isEqualByComparingTo("-3000.71");
        assertThat(row.breakdown().otherExpenses()).isEqualByComparingTo("-3987.73");
    }

    @Test
    @DisplayName("2026-09-26: доходы, расходы и к выплате сходятся с ручным подсчётом")
    void day2026_09_26() {
        var report = syncAndReport(DAY_2026_09_26, LocalDate.of(2026, 9, 26));

        var row = report.days().getFirst();
        assertThat(row.income()).isEqualByComparingTo("25280.00");
        assertThat(row.expenses()).isEqualByComparingTo("16662.03");
        assertThat(row.payout()).isEqualByComparingTo("8617.97");
        assertThat(report.reconciled()).isTrue();
        assertThat(row.breakdown().returns()).as("возвратов в этот день не было")
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Прочие расходы расшифрованы названиями из справочника")
    void expensesByTypeUseDictionaryNames() {
        var report = syncAndReport(DAY_2026_04_10, LocalDate.of(2026, 4, 10));

        var byType = report.days().getFirst().expensesByType();
        assertThat(byType).isNotEmpty();
        assertThat(byType).allSatisfy(item ->
                assertThat(item.name()).as("тип %s должен иметь название", item.typeId()).isNotBlank());

        assertThat(byType.getFirst().name()).isEqualTo("PayPerClick");
        assertThat(byType.getFirst().amount()).isEqualByComparingTo("-2267.09");

        BigDecimal sum = byType.stream()
                .map(ru.analizer.analytics.domain.FinancialSummary.TypeAmount::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo("-3987.73");
    }

    @Test
    @DisplayName("Период из нескольких дней даёт сумму и показывает пустые дни")
    void multiDayPeriod() {
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(LocalDate.of(2026, 4, 10), DAY_2026_04_10);
        FixtureAdapters.FIXTURES.put(LocalDate.of(2026, 9, 26), DAY_2026_09_26);
        accrualImportService.refreshAccrualTypes(accountId());
        accrualImportService.importAccruals(accountId(), LocalDate.of(2026, 4, 9), LocalDate.of(2026, 4, 11));

        // Только 10-е число содержит операции, 9-е и 11-е — пустые, но присутствуют.
        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(),
                LocalDate.of(2026, 4, 9), LocalDate.of(2026, 4, 11));

        assertThat(report.days()).hasSize(3);
        assertThat(report.days().get(0).date()).isEqualTo(LocalDate.of(2026, 4, 9));
        assertThat(report.days().get(0).income()).isEqualByComparingTo("0");
        assertThat(report.days().get(0).payout()).isEqualByComparingTo("0");
        assertThat(report.days().get(1).payout()).isEqualByComparingTo("11297.23");
        assertThat(report.days().get(2).payout()).isEqualByComparingTo("0");
        assertThat(report.reconciled()).isTrue();
    }

    @Test
    @DisplayName("Итог за период равен сумме дней и сходится с данными OZON")
    void periodTotalMatchesDays() {
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(LocalDate.of(2026, 4, 10), DAY_2026_04_10);
        FixtureAdapters.FIXTURES.put(LocalDate.of(2026, 9, 26), DAY_2026_09_26);
        accrualImportService.refreshAccrualTypes(accountId());
        accrualImportService.importAccruals(accountId(), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 9, 26));

        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(),
                LocalDate.of(2026, 4, 10), LocalDate.of(2026, 9, 26));

        // 31 165,00 + 25 280,00 и 11 297,23 + 8 617,97
        assertThat(report.income()).isEqualByComparingTo("56445.00");
        assertThat(report.expenses()).isEqualByComparingTo("36529.80");
        assertThat(report.payout()).isEqualByComparingTo("19915.20");
        assertThat(report.reconciled()).isTrue();
        assertThat(report.total().reconciliationDiff()).isEqualByComparingTo("0");
    }

@Test
    @DisplayName("Аккаунт есть, но ничего не импортировано — честный NOT_LOADED")
    void accountWithoutImportsReportsNotLoaded() {
        // Нули читаются как «денег не было». Отсутствие импорта — тоже «данных нет»,
        // поэтому отвечаем статусом NOT_LOADED с перечнем недостающих дней.
        account();
        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(),
                LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 10));

        assertThat(report.status()).isEqualTo(ReportStatus.NOT_LOADED);
        assertThat(report.coverage().loadedDays()).isZero();
        assertThat(report.coverage().missingDays())
                .containsExactly(LocalDate.of(2026, 4, 10));
        assertThat(report.coverage().needsSync()).isTrue();
    }

    @Test
    @DisplayName("Синхронизированный аккаунт без операций за период даёт нули")
    void syncedAccountWithoutDataReturnsZeros() {
        syncAndReport(DAY_2026_04_10, LocalDate.of(2026, 4, 10));

        // Аккаунт есть, но за выбранный день операций не было.
        var report = analytics.dailyReport(MARKETPLACE, accountIdOpt(),
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 1));

        assertThat(report.days()).hasSize(1);
        assertThat(report.income()).isEqualByComparingTo("0");
        assertThat(report.expenses()).isEqualByComparingTo("0");
        assertThat(report.payout()).isEqualByComparingTo("0");
        assertThat(report.reconciled()).isTrue();
    }
}