package ru.analizer.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;
import ru.analizer.account.domain.MarketplaceCredentials;
import ru.analizer.integration.ozon.OzonClient;
import ru.analizer.integration.ozon.OzonProperties;

/**
 * Клиент OZON, который не ходит в сеть.
 *
 * <p>Нужен с тех пор, как подключение аккаунта начало спрашивать у маркетплейса
 * название магазина. Интеграционные тесты подключения создают аккаунт, и раньше это
 * обходилось без обращения наружу — теперь нет, а без заглушки тесты уходили бы в
 * настоящий OZON с выдуманными ключами. Это медленно, нестабильно и зависит от чужого
 * сервиса: тест проходил бы или падал не из-за нашего кода.
 *
 * <p>Подменяется только этот метод: остальные запросы в тестах не выполняются,
 * потому что финансовый и каталожный адаптеры подменены своими заглушками.
 */
public class StubOzonClient extends OzonClient {

    /** Название, которое возвращает заглушка вместо настоящего ответа OZON. */
    public static final String SHOP_NAME = "ООО «ИД Академии Жуковского»";

    public StubOzonClient() {
        super(RestClient.builder(), new OzonProperties("https://ozon.invalid",
                java.time.Duration.ofSeconds(1), java.time.Duration.ofSeconds(1),
                0, java.time.Duration.ZERO));
    }

    @Override
    public String getShopName(MarketplaceCredentials credentials) {
        return SHOP_NAME;
    }
}
