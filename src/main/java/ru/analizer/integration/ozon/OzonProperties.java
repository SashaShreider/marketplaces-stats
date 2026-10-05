package ru.analizer.integration.ozon;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Настройки доступа к OZON.
 *
 * <p><b>Реквизитов здесь нет.</b> Идентификатор клиента и ключ задаёт пользователь
 * через {@code PUT /api/marketplaces/ozon/credentials}, они лежат в таблице
 * {@code seller_account} и приходят в каждый вызов отдельно.
 *
 * <p>Раньше они читались из {@code OZON_CLIENT_ID} и {@code OZON_API_KEY}. Это было
 * неудобно по трём причинам: приложение работало только на одном продавце; ключи
 * приходилось зашивать в окружение; и, что хуже всего, тесты подхватывали
 * {@code .env} разработчика и уходили в настоящий OZON его ключами. Теперь такой
 * возможности нет физически.
 *
 * @param baseUrl      https://api-seller.ozon.ru
 * @param connectTimeout сколько ждать установления соединения
 * @param readTimeout  сколько ждать ответа
 * @param maxRetries   сколько раз повторять запрос при временной ошибке
 * @param retryBackoff пауза перед повтором
 */
@ConfigurationProperties(prefix = "ozon")
public record OzonProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        int maxRetries,
        Duration retryBackoff
) {
}