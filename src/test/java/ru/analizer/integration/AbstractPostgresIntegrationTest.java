package ru.analizer.integration;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ru.analizer.marketplace.AccrualDto;
import ru.analizer.marketplace.MarketplaceAdapter;
import ru.analizer.marketplace.ozon.OzonMapper;
import ru.analizer.marketplace.ozon.dto.FinanceAccrual;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Интеграционная проверка сохранения на настоящем PostgreSQL (Testcontainers).
 *
 * <p>Адаптер подменён на фикстурный: реальный HTTP не используется, но путь данных
 * тот же, что в бою — разбор JSON в DTO, маппинг в домен, запись в БД.
 */
@SpringBootTest
@Import(AbstractPostgresIntegrationTest.FixtureAdapterConfig.class)
abstract class AbstractPostgresIntegrationTest {

    /**
     * Контейнер один на весь JVM и не останавливается автоматически.
     *
     * <p>Аннотации {@code @Testcontainers} + {@code @Container} останавливали бы контейнер
     * после каждого класса, тогда как Spring-контекст кэшируется и следующий класс получал
     * уже остановленную базу. Ручной запуск в статическом блоке это исключает.
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
    protected ru.analizer.analytics.DailyAnalyticsService analytics;

    @Autowired
    protected ru.analizer.sync.SyncDayService syncDayService;

    protected static final String CLIENT_ID = "1154";
    protected static final LocalDate DAY = LocalDate.of(2026, 4, 10);

    private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * Фикстурный адаптер: тот же контракт, что у OZON, но данные берутся из файла.
     * Ни одного обращения к api-seller.ozon.ru.
     */
    @TestConfiguration
    static class FixtureAdapterConfig {

        /**
         * День → файл с ответом API. Заполняется конкретным тестом.
         *
         * <p>Справочник типов — реальный ответ {@code /v1/finance/accrual/types},
         * а не выдуманный: угадывать названия бессмысленно, OZON может их менять,
         * и именно поэтому бизнес-логика не должна опираться на захардкоженный список.
         */
        static final Map<LocalDate, String> FIXTURES = new LinkedHashMap<>();

        static String accrualTypesJson() {
            try (InputStream in = AbstractPostgresIntegrationTest.class.getClassLoader()
                    .getResourceAsStream("fixtures/accrual-types-real.json")) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Не найдена фикстура справочника типов начислений", e);
            }
        }

        @Bean
        @Primary
        FixtureMarketplaceAdapter fixtureAdapter() {
            return new FixtureMarketplaceAdapter();
        }
    }

    static final class FixtureMarketplaceAdapter implements MarketplaceAdapter {

        @Override
        public String marketplaceCode() {
            return "OZON";
        }

        @Override
        public List<ru.analizer.marketplace.AccrualTypeInfo> fetchAccrualTypes() {
            var root = MAPPER.readTree(FixtureAdapterConfig.accrualTypesJson());
            List<ru.analizer.marketplace.AccrualTypeInfo> types = new ArrayList<>();
            for (var node : root.get("accrual_types")) {
                var type = MAPPER.treeToValue(node, ru.analizer.marketplace.ozon.dto.AccrualType.class);
                types.add(new ru.analizer.marketplace.AccrualTypeInfo(type.id(), type.name(), type.description()));
            }
            return types;
        }

        @Override
        public List<AccrualDto> fetchAccrualsByDay(LocalDate date) {
            String resource = FixtureAdapterConfig.FIXTURES.get(date);
            if (resource == null) {
                return List.of();
            }
            return parse(resource);
        }
    }

    static List<AccrualDto> parse(String resource) {
        try (InputStream in = open(resource)) {
            var root = MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<AccrualDto> result = new ArrayList<>();
            for (var node : root.get("accruals")) {
                result.add(OzonMapper.toAccrualDto(
                        MAPPER.treeToValue(node, FinanceAccrual.class), node.toString()));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать фикстуру " + resource, e);
        }
    }

    private static InputStream open(String resource) throws IOException {
        InputStream in = AbstractPostgresIntegrationTest.class.getClassLoader()
                .getResourceAsStream(resource);
        if (in == null) {
            in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(resource));
        }
        if (in == null) {
            throw new IOException("Фикстура не найдена: " + resource);
        }
        return in;
    }

    @BeforeEach
    void resetDatabase() {
        FixtureAdapterConfig.FIXTURES.clear();
        jdbc.execute("""
                TRUNCATE TABLE finance_accrual, posting, posting_product, delivery_service,
                               item_fee, item_fee_detail, non_item_fee, container_fee,
                               sync_day, sync_job, accrual_type, seller_account, marketplace
                RESTART IDENTITY CASCADE
                """);
        jdbc.update("INSERT INTO marketplace (code, name) VALUES ('OZON', 'OZON')");
    }

    protected long count(String table) {
        Long value = jdbc.queryForObject("select count(*) from " + table, Long.class);
        return value == null ? 0 : value;
    }

    protected BigDecimalAssert sumTotal(String table) {
        return new BigDecimalAssert(jdbc.queryForObject(
                "select coalesce(sum(total_amount), 0) from " + table, java.math.BigDecimal.class));
    }
}
