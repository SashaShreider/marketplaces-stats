package ru.analizer.account.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.analizer.account.domain.AccountLookup;
import ru.analizer.account.domain.MarketplaceCredentials;
import ru.analizer.integration.MarketplaceAdapter;

/**
 * Подключение маркетплейса.
 *
 * <p>Реквизиты вводит пользователь. Отдельный шаг после регистрации, а не часть
 * регистрации: ключи у маркетплейсов истекают, их приходится менять, и менять
 * аккаунт для этого не нужно.
 *
 * <p>Перед сохранением ключи проверяются настоящим запросом к маркетплейсу. Иначе
 * пользователь узнал бы о неверном ключе через три неудачных импорта.
 *
 * <p>Здесь же удаление магазина: подключение и отключение — одна фича, и держать
 * их в разных контроллерах незачем.
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

    /**
     * DELETE /api/marketplaces/ozon
     *
     * <p>Отключает магазин: удаляет аккаунт и все его данные — начисления, каталог,
     * авторов и прогоны импортов. Отдельные удаления для этого не нужны, в схеме стоит
     * {@code ON DELETE CASCADE}.
     *
     * <p>Необратимо. Ключи маркетплейса придётся ввести заново, и данные тоже
     * загрузится заново: с другим аккаунтом они могут оказаться другими.
     *
     * <p>Сам маркетплейс из справочника не убирается: он общий для всех, и после
     * удаления доступен для повторного подключения.
     *
     * @return 204, если магазин был подключён
     * @throws ru.analizer.web.NotConnectedException 409, если магазин не подключён
     */
    @DeleteMapping
    public ResponseEntity<Void> disconnect(@PathVariable String marketplace) {
        accountLookup.deleteAccount(marketplace);
        return ResponseEntity.noContent().build();
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
