package ru.analizer.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import ru.analizer.integration.ProductCatalogAdapter;

/**
 * Каталог товаров из сохранённого ответа OZON — вместо реального HTTP.
 */
@TestConfiguration
public class CatalogAdapterConfig {

    @Bean
    @Primary
    ProductCatalogAdapter catalogFixtureAdapter() {
        return CatalogFixtureAdapter.create();
    }
}