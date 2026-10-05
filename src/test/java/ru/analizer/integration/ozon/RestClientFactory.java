package ru.analizer.integration.ozon;

import org.springframework.web.client.RestClient;

/**
 * Заглушка {@link RestClient.Builder} для тестов, которым HTTP не нужен.
 * Реальный {@code RestClient} создаётся, но ни один запрос не выполняется.
 */
final class RestClientFactory {

    private RestClientFactory() {
    }

    static RestClient.Builder noop() {
        return RestClient.builder();
    }
}
