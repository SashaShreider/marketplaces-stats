package ru.analizer.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.analizer.marketplace.MarketplaceAdapter;

/**
 * Адаптер, синтезирующий данные за любую дату — для проверки длинных периодов.
 *
 * <p>Отдельная конфигурация вместо {@link FixtureAdapterConfig}: оба адаптера помечены
 * {@code @Primary}, и в одном контексте они конфликтовали бы. Тест выбирает нужный явно
 * через {@code @Import}.
 */
@TestConfiguration
public class BulkAdapterConfig {

    @Bean
    @Primary
    MarketplaceAdapter bulkMarketplaceAdapter() {
        BulkFixtureAdapter adapter = new BulkFixtureAdapter();
        FixtureAdapters.setBulkAdapter(adapter);
        return adapter;
    }
}