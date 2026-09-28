package ru.analizer.marketplace.ozon;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Реквизиты подключения к OZON. Значения приходят из переменных окружения,
 * в репозитории их нет (см. .env.example).
 *
 * @param clientId     X-Client-Id, значение OZON_CLIENT_ID
 * @param apiKey       X-Api-Key, значение OZON_API_KEY; в БД не сохраняется
 * @param baseUrl      https://api-seller.ozon.ru
 */
@ConfigurationProperties(prefix = "ozon")
public record OzonProperties(
        String baseUrl,
        String clientId,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout,
        int maxRetries,
        Duration retryBackoff
) {
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && apiKey != null && !apiKey.isBlank();
    }
}
