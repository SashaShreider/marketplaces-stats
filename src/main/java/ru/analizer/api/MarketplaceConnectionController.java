package ru.analizer.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.marketplace.MarketplaceAdapter;
import ru.analizer.marketplace.MarketplaceCredentials;
import ru.analizer.persistence.AccountLookup;

/**
 * Подключение маркетплейса.
 *
 * <p>Реквизиты вводит пользователь. Отдельный шаг после регистрации, а не часть
 * регистрации: ключи у маркетплейсов истекают, их приходится менять, и менять
 * аккаунт для этого не нужно.
 *
 * <p>Перед сохранением ключи проверяются настоящим запросом к маркетплейсу. Иначе
 * пользователь узнал бы о неверном ключе через три неудачных импорта.
 */
@RestController
@RequestMapping("/api/marketplaces/{marketplace}")
public class MarketplaceConnectionController {

    private final AccountLookup accountLookup;
    private final MarketplaceAdapter marketplaceAdapter;

    public MarketplaceConnectionController(AccountLookup accountLookup,
                                           MarketplaceAdapter marketplaceAdapter) {
        this.accountLookup = accountLookup;
        this.marketplaceAdapter = marketplaceAdapter;
    }

    /**
     * PUT /api/marketplaces/ozon/credentials
     *
     * <p>Подключает маркетплейс или заменяет реквизиты уже подключённого. Идемпотентно:
     * повторная отправка тех же ключей просто перезаписывает их.
     *
     * <p>Ответ никогда не содержит {@code apiKey} — ни целиком, ни частью.
     */
    @PutMapping("/credentials")
    public ResponseEntity<?> connect(@PathVariable String marketplace,
                                    @RequestBody CredentialsRequest request) {
        accountLookup.requireMarketplace(marketplace);
        MarketplaceCredentials credentials = new MarketplaceCredentials(
                request.clientId(), request.apiKey());

        // Проверка настоящим запросом: неверный ключ должен отклоняться сейчас,
        // а не через три импорта, где пользователь уже разобрался, что делает.
        marketplaceAdapter.verifyCredentials(credentials);

        AccountLookup.ConnectedAccount account = accountLookup.ensureAccount(marketplace, credentials);
        return ResponseEntity.ok(new ConnectedInfo(account.marketplaceCode(), account.clientId()));
    }

    /** @param clientId идентификатор клиента маркетплейса
     *  @param apiKey   секретный ключ; в ответе не возвращается */
    public record CredentialsRequest(String clientId, String apiKey) {
    }

    /** @param marketplace код маркетплейса
     *  @param clientId   подключённый идентификатор клиента */
    public record ConnectedInfo(String marketplace, String clientId) {
    }
}
