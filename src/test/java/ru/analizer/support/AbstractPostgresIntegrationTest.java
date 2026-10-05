package ru.analizer.support;

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
import ru.analizer.account.domain.Marketplace;
import ru.analizer.account.repository.MarketplaceRepository;
import ru.analizer.account.repository.SellerAccountRepository;
import ru.analizer.analytics.infrastructure.CatalogFacts;
import ru.analizer.auth.repository.AppUserRepository;
import ru.analizer.catalog.application.CatalogImportService;
import ru.analizer.sync.application.AccrualImportService;
import ru.analizer.sync.application.DayStateService;
import ru.analizer.sync.application.ImportService;
import ru.analizer.sync.repository.ImportRunRepository;

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
public abstract class AbstractPostgresIntegrationTest {

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
    protected org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

@Autowired
    protected ru.analizer.sync.application.AccrualImportService accrualImportService;

    @Autowired
    protected ru.analizer.sync.application.ImportService importService;

    @Autowired
    protected ru.analizer.analytics.application.DailyAnalyticsService analytics;

    @Autowired
    protected ru.analizer.analytics.application.ProductAnalyticsService productAnalytics;

    @Autowired
    protected ru.analizer.catalog.application.CatalogImportService catalogImportService;

    @Autowired
    protected ru.analizer.analytics.infrastructure.CatalogFacts catalogFacts;

    @Autowired
    protected ru.analizer.sync.application.DayStateService dayStateService;

    @Autowired
    protected ru.analizer.sync.repository.ImportRunRepository importRunRepository;

    @Autowired
    protected ru.analizer.account.repository.MarketplaceRepository marketplaceRepository;

    @Autowired
    protected ru.analizer.account.repository.SellerAccountRepository sellerAccountRepository;

    @Autowired
    protected ru.analizer.auth.repository.AppUserRepository appUserRepository;

    protected static final String CLIENT_ID = "1154";

    /**
     * Логин тестового пользователя.
     *
     * <p>HTTP-тесты подставляют его в аутентификацию через {@code @WithMockUser}, а
     * сервисные получают аккаунт этого пользователя напрямую. Пользователь должен
     * существовать в базе: {@code AccountLookup} ищет его по логину из сессии, и если
     * его нет, тесты падали бы с «сессия указывает на несуществующего пользователя».
     */
    protected static final String LOGIN = "test-user";

    /** Пароль тестового пользователя. */
    protected static final String PASSWORD = "test-password-1";

    /** Код маркетплейса, подставляемый в путь запроса. */
    protected static final String MARKETPLACE = "OZON";

    /** Ключи тестового аккаунта; настоящий адаптер подменён, поэтому значения любые. */
    protected static final String API_KEY = "test-api-key";

    /** Дата, которую используют тесты одиночных дней. */
    protected static final java.time.LocalDate DAY = java.time.LocalDate.of(2026, 4, 10);

    /**
     * Идентификатор аккаунта теста, создавая его при необходимости.
     *
     * <p>Идентификатор аккаунта приходит из пути запроса, а не от клиента, поэтому тестам
     * нужно получить его у репозитория — ровно так же, как это делает контроллер.
     */
    protected Long accountId() {
        return account().getId();
    }

    /**
     * Аккаунт тестового пользователя, создавая его при необходимости.
     *
     * <p>У аккаунта обязателен {@code api_key}: фоновые задачи берут из него ключи для
     * запросов к маркетплейсу, и без него импорт падал бы на середине.
     */
    protected ru.analizer.account.domain.SellerAccount account() {
        return sellerAccountRepository
                .findByUserIdAndMarketplaceId(user().getId(), marketplace().getId())
                .orElseGet(() -> sellerAccountRepository.save(
                        new ru.analizer.account.domain.SellerAccount(
                                user(), marketplace(), MARKETPLACE + " " + CLIENT_ID,
                                CLIENT_ID, API_KEY)));
    }

    /**
     * Идентификатор аккаунта для вызовов сервисов.
     *
     * <p>Сервисы принимают аккаунт готовым: искать его внутри них нельзя, поиск идёт
     * через сессию, а она есть только в потоке запроса. Поэтому тесты, работающие с
     * сервисами напрямую, передают аккаунт сами — ровно так же, как это делает контроллер.
     */
    protected java.util.Optional<Long> accountIdOpt() {
        return java.util.Optional.of(account().getId());
    }

    /** Пользователь, которому принадлежат тестовые данные. */
    protected ru.analizer.auth.domain.AppUser user() {
        return appUserRepository.findByLogin(LOGIN)
                .orElseThrow(() -> new AssertionError("Тестовый пользователь не найден"));
    }

    protected ru.analizer.account.domain.Marketplace marketplace() {
        return marketplaceRepository.findByCode(MARKETPLACE)
                .orElseThrow(() -> new AssertionError("Маркетплейс не найден"));
    }

    @BeforeEach
    void resetDatabase() {
        awaitBackgroundJobs();
        FixtureAdapters.clearAll();
jdbc.execute("""
                TRUNCATE TABLE finance_accrual, posting, posting_product, delivery_service,
                               item_fee, item_fee_detail, non_item_fee, container_fee,
                               imported_day, import_run, accrual_type, seller_account, marketplace,
                               product_author, ozon_product_attribute, ozon_product,
                               app_user
                RESTART IDENTITY CASCADE
                """);
        jdbc.update("INSERT INTO marketplace (code, name) VALUES ('OZON', 'OZON')");
        // Пользователь заводится в каждом тесте заново: без него не работает AccountLookup,
        // а аккаунту он принадлежит по внешнему ключу. Хэш фиктивный — настоящий вход
        // проверяется HTTP-тестами через /api/auth/register.
        // Хэш настоящий: HTTP-тесты входят под этим паролем через /api/auth/login.
        // Фиктивная строка означала бы, что вход невозможен, а проверялся бы отказ.
        jdbc.update("""
                INSERT INTO app_user (login, password_hash, display_name)
                VALUES (?, ?, 'Тестовый пользователь')
                """, LOGIN, passwordEncoder.encode(PASSWORD));
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
                    select count(*) from import_run
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

    /**
     * Ждёт завершения фоновой задачи.
     *
     * <p>Загрузка идёт в отдельном потоке, поэтому тест не может просто посмотреть на
     * статус сразу после вызова — он ещё PENDING. Ожидание по факту завершения надёжнее
     * проверки статуса: так тест упадёт на зависшей задаче, а не пройдёт по счастливой
     * последовательности.
     */
    protected ru.analizer.sync.domain.ImportProgress awaitJob(Long importId) {
        java.time.Instant deadline = java.time.Instant.now().plus(Duration.ofMinutes(3));
        while (java.time.Instant.now().isBefore(deadline)) {
            var progress = importService.progressOf(importId, accountId()).orElseThrow();
            if (progress.finished()) {
                if (progress.status() == ru.analizer.sync.domain.RunState.FAILED) {
                    throw new AssertionError("Прогон " + importId + " провалился: " + progress.error());
                }
                return progress;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Ожидание прогона прервано");
            }
        }
throw new AssertionError("Прогон " + importId + " не завершился за 3 минуты");
    }

    protected BigDecimalAssert sumTotal(String table) {
        return new BigDecimalAssert(jdbc.queryForObject(
                "select coalesce(sum(total_amount), 0) from " + table, BigDecimal.class));
    }

    /** Проверка суммы читается в тестах как {@code bd(x).is("11297.23")}. */
    protected static final class BigDecimalAssert {

        private final BigDecimal actual;

        public BigDecimalAssert(BigDecimal actual) {
            this.actual = actual;
        }

        public void is(String expected) {
            assertThat(actual).isEqualByComparingTo(new BigDecimal(expected));
        }

        public void isNegative() {
            assertThat(actual.signum()).isNegative();
        }

        public void isZero() {
            assertThat(actual.signum()).isZero();
        }
    }
}
