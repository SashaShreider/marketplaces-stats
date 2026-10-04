package ru.analizer.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционная проверка на настоящем PostgreSQL (Testcontainers).
 *
 * <p>Адаптер маркетплейса подменяет конкретный тест: {@link FixtureAdapterConfig} для
 * одиночных дней и {@link BulkAdapterConfig} для длинных периодов. Реальный HTTP не
 * используется, но путь данных тот же, что в бою — разбор JSON в DTO, маппинг в домен,
 * запись в БД.
 *
 * <p>Контейнер поднимается один на весь JVM и работает в отдельной базе: данные тестов
 * не попадают в базу разработки.
 */
@SpringBootTest
abstract class AbstractPostgresIntegrationTest {

    /**
     * Контейнер один на весь JVM и не останавливается автоматически.
     *
     * <p>Аннотации {@code @Testcontainers} + {@code @Container} останавливали бы контейнер
     * после каждого класса, тогда как Spring-контекст кэшируется и следующий класс получал
     * бы уже остановленную базу. Ручной запуск в статическом блоке это исключает.
     */
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("analizer")
                .withUsername("analizer")
                .withPassword("analizer");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ru.analizer.sync.SyncService syncService;

    @Autowired
    protected ru.analizer.sync.SyncJobService syncJobService;

    @Autowired
    protected ru.analizer.analytics.DailyAnalyticsService analytics;

    @Autowired
    protected ru.analizer.sync.SyncDayService syncDayService;

    @Autowired
    protected ru.analizer.persistence.repository.SyncJobRepository syncJobRepository;

    @Autowired
    protected ru.analizer.persistence.repository.MarketplaceRepository marketplaceRepository;

    @Autowired
    protected ru.analizer.persistence.repository.SellerAccountRepository sellerAccountRepository;

    protected static final String CLIENT_ID = "1154";

    /** Дата, которую используют тесты одиночных дней. */
    protected static final java.time.LocalDate DAY = java.time.LocalDate.of(2026, 4, 10);

    @BeforeEach
    void resetDatabase() {
        awaitBackgroundJobs();
        FixtureAdapters.clearAll();
        jdbc.execute("""
                TRUNCATE TABLE finance_accrual, posting, posting_product, delivery_service,
                               item_fee, item_fee_detail, non_item_fee, container_fee,
                               sync_day, sync_job, accrual_type, seller_account, marketplace
                RESTART IDENTITY CASCADE
                """);
        jdbc.update("INSERT INTO marketplace (code, name) VALUES ('OZON', 'OZON')");
    }

    /**
     * Дожидаемся фоновых загрузок перед очисткой базы.
     *
     * <p>TRUNCATE требует эксклюзивной блокировки на всех таблицах, а фоновая задача
     * продолжает писать, пока тест считает её завершённой по статусу. Без этого ожидания
     * PostgreSQL сообщает о deadlock между очисткой и загрузкой.
     */
    private void awaitBackgroundJobs() {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        while (Instant.now().isBefore(deadline)) {
            Integer active = jdbc.queryForObject("""
                    select count(*) from sync_job
                    where status in ('PENDING', 'RUNNING')
                    """, Integer.class);
            if (active == null || active == 0) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("Фоновые задачи синхронизации не завершились за 2 минуты");
    }

    protected long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0 : value;
    }

    protected BigDecimalAssert sumTotal(String table) {
        return new BigDecimalAssert(jdbc.queryForObject(
                "select coalesce(sum(total_amount), 0) from " + table, BigDecimal.class));
    }

    /** Проверка суммы читается в тестах как {@code bd(x).is("11297.23")}. */
    protected static final class BigDecimalAssert {

        private final BigDecimal actual;

        BigDecimalAssert(BigDecimal actual) {
            this.actual = actual;
        }

        void is(String expected) {
            assertThat(actual).isEqualByComparingTo(new BigDecimal(expected));
        }

        void isNegative() {
            assertThat(actual.signum()).isNegative();
        }

        void isZero() {
            assertThat(actual.signum()).isZero();
        }
    }
}