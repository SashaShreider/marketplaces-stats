package ru.analizer.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.analizer.integration.MarketplaceAdapter;
import ru.analizer.integration.ProductCatalogAdapter;
import ru.analizer.integration.ozon.OzonClient;
import ru.analizer.integration.model.AccrualDto;
import ru.analizer.integration.model.AccrualTypeInfo;

import java.time.LocalDate;
import java.util.List;

/**
 * Адаптеры, отдающие сохранённые ответы OZON за конкретные даты и по каталогу.
 *
 * <p>Используется тестами одиночных дней: берёт файл-фикстуру и подставляет его как
 * ответ настоящего API. Реальные обращения к OZON не выполняются.
 *
 * <p>Каталог живёт здесь же, а не в отдельной конфигурации: каждый набор
 * {@code @Import} — это отдельный контекст Spring, а контекст поднимается десятки
 * секунд. Конфигураций было три, контекстов выходило семь; сейчас конфигурация одна
 * (кроме отдельной для длинных периодов), и контекстов вдвое меньше.
 */
@TestConfiguration
public class FixtureAdapterConfig {

    @Bean
    @Primary
    OzonClient stubOzonClient() {
        return new StubOzonClient();
    }

    @Bean
    @Primary
    ProductCatalogAdapter fixtureCatalogAdapter() {
        return CatalogFixtureAdapter.create();
    }

    @Bean
    @Primary
    MarketplaceAdapter fixtureMarketplaceAdapter() {
        return new MarketplaceAdapter() {
            @Override
            public String marketplaceCode() {
                return "OZON";
            }

                        @Override
            // Реквизиты в тестах не проверяются по-настоящему: адаптер подменён.
            // Но ключ из FixtureAdapters.REJECTED_KEY всё-таки отвергается — иначе
            // нельзя проверить, что отказ маркетплейса не оставляет после себя аккаунт.
            public void verifyCredentials(ru.analizer.account.domain.MarketplaceCredentials credentials) {
                if (FixtureAdapters.REJECTED_KEY.equals(credentials.apiKey())) {
                    throw new ru.analizer.integration.CredentialsRejectedException(
                            ru.analizer.integration.ozon.OzonAdapter.MARKETPLACE_CODE, 401);
                }
            }

            @Override
            public List<AccrualTypeInfo> fetchAccrualTypes(ru.analizer.account.domain.MarketplaceCredentials credentials) {
                return FixtureAdapters.types();
            }

            @Override
            public List<AccrualDto> fetchAccrualsByDay(ru.analizer.account.domain.MarketplaceCredentials credentials, LocalDate date) {
                String resource = FixtureAdapters.FIXTURES.get(date);
                if (resource == null) {
                    return List.of();
                }
                FixtureAdapters.delay();
                return FixtureAdapters.parse(resource);
            }
        };
    }
}