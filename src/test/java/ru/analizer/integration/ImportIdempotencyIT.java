package ru.analizer.integration;

import org.springframework.context.annotation.Import;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.sync.PeriodCoverage;
import ru.analizer.sync.AccrualImportReport;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контрольная точка IMPLEMENTATION §19 Этап 5: повторная синхронизация того же периода
 * не создаёт дубликатов.
 *
 * <p>Отдельно проверяется, что дочерние строки не накапливаются: при обновлении операции
 * детализация пересобирается, а не добавляется поверх прежней.
 */
@Import(FixtureAdapterConfig.class)
class SyncIdempotencyIT extends AbstractPostgresIntegrationTest {

    private static final String DAY_2026_04_10 = "fixtures/accruals-2026-04-10.json";

    private AccrualImportReport firstSync() {
        FixtureAdapters.FIXTURES.put(DAY, DAY_2026_04_10);
        accrualImportService.refreshAccrualTypes(accountId());
        return accrualImportService.importAccruals(accountId(), DAY, DAY);
    }

    @Test
    @DisplayName("Второй запуск за тот же день не создаёт новых операций")
    void secondRunDoesNotInsertDuplicates() {
        AccrualImportReport first = firstSync();
        assertThat(first.accrualsInserted()).isEqualTo(94);
        assertThat(first.accrualsUpdated()).isZero();
        assertThat(first.complete()).isTrue();

        AccrualImportReport second = accrualImportService.importAccruals(accountId(), DAY, DAY);

        // 2026-04-10 давно старше окна зрелости, поэтому день окончательный и повторно
        // не запрашивается: никаких обращений к OZON и никаких дублей в базе.
        assertThat(second.accrualsInserted()).isZero();
        assertThat(second.accrualsUpdated()).isZero();
        assertThat(second.accrualsReceived()).as("окончательный день не перезапрашивается").isZero();
        assertThat(second.syncedDays()).isZero();
        assertThat(count("finance_accrual")).isEqualTo(94);
    }

    @Test
    @DisplayName("Третий запуск тоже стабилен, а суммы не меняются")
    void repeatedRunsAreStable() {
        firstSync();
        accrualImportService.importAccruals(accountId(), DAY, DAY);
        AccrualImportReport third = accrualImportService.importAccruals(accountId(), DAY, DAY);

        assertThat(third.accrualsInserted()).isZero();
        assertThat(count("finance_accrual")).isEqualTo(94);
        sumTotal("finance_accrual").is("11297.23");
    }

    @Test
    @DisplayName("Свежий день внутри окна зрелости перезапрашивается и обновляется")
    void provisionalDayIsRefreshedOnNextRun() {
        // Главная причина, по которой окно зрелости вообще нужно: начисления за свежие
        // дни продолжают приходить. Если бы повторный запуск пропускал загруженные дни,
        // данные за вчерашний день так и остались бы неполными навсегда.
        LocalDate yesterday = LocalDate.now().minusDays(1);
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(yesterday, DAY_2026_04_10);

        accrualImportService.refreshAccrualTypes(accountId());
        AccrualImportReport first = accrualImportService.importAccruals(accountId(), yesterday, yesterday);
        assertThat(first.accrualsInserted()).isEqualTo(94);

        // День загружен, но он внутри окна зрелости — значит не окончательный.
        var coverage = dayStateService.coverage(clientId(), yesterday, yesterday);
        assertThat(coverage.loadedDays()).isEqualTo(1);
        assertThat(coverage.finalDays()).as("вчерашний день ещё не окончателен").isZero();
        assertThat(coverage.provisionalDays()).containsExactly(yesterday);
        assertThat(coverage.allFinal()).isFalse();

        AccrualImportReport second = accrualImportService.importAccruals(accountId(), yesterday, yesterday);
        assertThat(second.accrualsReceived()).as("свежий день запрашивается заново").isEqualTo(94);
        assertThat(second.accrualsUpdated()).as("данные обновились, а не продублировались").isEqualTo(94);
        assertThat(second.accrualsInserted()).isZero();
        assertThat(count("finance_accrual")).isEqualTo(94);
    }

    private Long clientId() {
        return jdbc.queryForObject("select id from seller_account limit 1", Long.class);
    }

    @Test
    @DisplayName("Дочерние строки не накапливаются при повторной синхронизации")
    void childRowsDoNotAccumulate() {
        firstSync();
        long postings = count("posting");
        long products = count("posting_product");
        long deliveryServices = count("delivery_service");
        long itemFees = count("item_fee");
        long itemFeeDetails = count("item_fee_detail");
        long nonItemFees = count("non_item_fee");

        accrualImportService.importAccruals(accountId(), DAY, DAY);

        assertThat(count("posting")).isEqualTo(postings);
        assertThat(count("posting_product")).isEqualTo(products);
        assertThat(count("delivery_service")).isEqualTo(deliveryServices);
        assertThat(count("item_fee")).isEqualTo(itemFees);
        assertThat(count("item_fee_detail")).isEqualTo(itemFeeDetails);
        assertThat(count("non_item_fee")).isEqualTo(nonItemFees);

        // Итоговые значения, сверенные вручную на реальном ответе OZON.
        assertThat(postings).isEqualTo(26);
        assertThat(products).isEqualTo(26);
        assertThat(deliveryServices).isEqualTo(62);
        assertThat(itemFees).isEqualTo(53);
        assertThat(itemFeeDetails).isEqualTo(53);
        assertThat(nonItemFees).isEqualTo(15);
    }

    @Test
    @DisplayName("Справочник типов начислений тоже обновляется, а не дублируется")
    void accrualTypesAreIdempotent() {
        firstSync();
        int initial = (int) count("accrual_type");
        assertThat(initial).isEqualTo(132);

        accrualImportService.refreshAccrualTypes(accountId());
        accrualImportService.refreshAccrualTypes(accountId());

        assertThat(count("accrual_type")).isEqualTo(initial);
    }

    @Test
    @DisplayName("Аккаунт продавца не создаётся повторно")
    void sellerAccountIsNotDuplicated() {
        firstSync();
        accrualImportService.importAccruals(accountId(), DAY, DAY);

        assertThat(count("seller_account")).isEqualTo(1);
        assertThat(count("marketplace")).isEqualTo(1);
    }

    @Test
    @DisplayName("Один уникальный ключ не даёт задвоить accrual_id")
    void uniqueConstraintProtectsFromDuplicates() {
        firstSync();

        // Прямая попытка вставить дубль должна быть отвергнута базой, а не приложением.
        Long accountId = jdbc.queryForObject("select id from seller_account limit 1", Long.class);
        java.math.BigDecimal existing = jdbc.queryForObject(
                "select total_amount from finance_accrual where external_id = 48762627746",
                java.math.BigDecimal.class);

        org.springframework.dao.DataIntegrityViolationException error =
                org.junit.jupiter.api.Assertions.assertThrows(
                        org.springframework.dao.DataIntegrityViolationException.class,
                        () -> jdbc.update("""
                                insert into finance_accrual
                                    (seller_account_id, external_id, accrual_date, accrued_category,
                                     total_amount, currency, raw_data)
                                values (?, ?, ?, 'POSTING', ?, 'RUB', '{}'::jsonb)
                                """, accountId, 48762627746L, java.sql.Date.valueOf(DAY), existing));

        assertThat(error).hasMessageContaining("uq_finance_accrual_account_external");
        assertThat(count("finance_accrual")).isEqualTo(94);
    }
}