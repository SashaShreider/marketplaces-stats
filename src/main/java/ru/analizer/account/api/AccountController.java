package ru.analizer.account.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import ru.analizer.account.domain.AccountLookup;
import ru.analizer.account.domain.MarketplaceCredentials;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.catalog.repository.OzonProductRepository;
import ru.analizer.integration.MarketplaceAdapter;
import ru.analizer.sync.repository.ImportRunRepository;

import java.util.List;

/**
 * Магазины пользователя: список, подключение, отключение, смена ключей.
 *
 * <p>Магазин — это то, с чем работают импорты и аналитика. У пользователя их может быть
 * сколько угодно, в том числе несколько на одной платформе: у продавца бывает
 * несколько кабинетов OZON, и у каждого свои начисления и каталог.
 *
 * <p>Отдельное от {@link MarketplaceController} подключение — по той же причине, по
 * которой список разделён со справочником платформ: подключение создаёт магазин, а
 * знать, какие платформы вообще поддерживаются, для этого не нужно.
 *
 * <p>Реквизиты вводит пользователь. Это отдельный шаг, а не часть регистрации: ключи
 * у маркетплейсов истекают, их приходится менять, и для этого менять аккаунт не нужно.
 *
 * <p>Перед сохранением ключи проверяются настоящим запросом к маркетплейсу. Иначе
 * пользователь узнал бы о неверном ключе через три неудачных импорта.
 */
@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountLookup accountLookup;
    private final MarketplaceAdapter marketplaceAdapter;
    private final OzonProductRepository ozonProductRepository;
    private final ImportRunRepository importRunRepository;

    public AccountController(AccountLookup accountLookup,
                             MarketplaceAdapter marketplaceAdapter,
                             OzonProductRepository ozonProductRepository,
                             ImportRunRepository importRunRepository) {
        this.accountLookup = accountLookup;
        this.marketplaceAdapter = marketplaceAdapter;
        this.ozonProductRepository = ozonProductRepository;
        this.importRunRepository = importRunRepository;
    }

    /**
     * GET /api/accounts
     *
     * <p>Магазины текущего пользователя. Собственных запросов к маркетплейсам не
     * выполняет, только состояние нашей базы.
     *
     * <p>Ответ содержит {@code accountId} — его подставляют в путь всех прочих запросов.
     * В прежней схеме магазин выбирался кодом маркетплейса, и это работало, пока
     * магазин на платформе был один: с несколькими кабинетами одного продавца выбор
     * стал неоднозначным, а молча взять не тот — значит показать чужие данные.
     */
    @GetMapping
    public List<AccountInfo> list() {
        return accountLookup.findAccounts().stream()
                .map(this::toInfo)
                .toList();
    }

    /**
     * POST /api/accounts
     *
     * <p>Подключает новый магазин. Код маркетплейса приходит в теле запроса: это тип
     * создаваемого ресурса, и больше он в контракте не встречается.
     *
     * <p>Ответ никогда не содержит {@code apiKey} — ни целиком, ни частью.
     *
     * @throws ru.analizer.web.UnknownMarketplaceException такого маркетплейса нет
     * @throws ru.analizer.web.ShopAlreadyConnectedException этот кабинет уже подключён
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountInfo connect(@RequestBody CreateAccountRequest request) {
        // Неизвестный маркетплейс отсекается здесь же: иначе provisioner для него
        // не нашёлся бы уже в createAccount, с сообщением внут��еннего сбоя.
        accountLookup.requireMarketplace(request.marketplace());
        MarketplaceCredentials credentials = new MarketplaceCredentials(
                request.clientId(), request.apiKey());

        // Проверка настоящим запросом: неверный ключ должен отклоняться сейчас,
        // а не через три импорта, где пользователь уже разобрался, что делает.
        marketplaceAdapter.verifyCredentials(credentials);

        AccountLookup.ConnectedAccount account =
                accountLookup.createAccount(request.marketplace(), credentials);
        return toInfo(account.id());
    }

    /**
     * PUT /api/accounts/{accountId}/credentials
     *
     * <p>Заменяет реквизиты уже подключённого магазина. Отдельное действие от
     * подключения: магазин не создаётся, а чинится тот, что уже есть. Иначе пришлось бы
     * различать эти случаи по наличию идентификатора в пути, а это различие нельзя
     * додумывать на клиенте.
     *
     * <p>Идемпотентно: повторная отправка тех же ключей просто перезаписывает их.
     *
     * @throws ru.analizer.web.AccountNotFoundException магазина нет или он не этот
     */
    @PutMapping("/{accountId}/credentials")
    public AccountInfo updateCredentials(@PathVariable Long accountId,
                                         @RequestBody CredentialsRequest request) {
        MarketplaceCredentials credentials = new MarketplaceCredentials(
                request.clientId(), request.apiKey());
        marketplaceAdapter.verifyCredentials(credentials);
        accountLookup.updateCredentials(accountId, credentials);
        return toInfo(accountId);
    }

    /**
     * DELETE /api/accounts/{accountId}
     *
     * <p>Отключает магазин: удаляет аккаунт и все его данные — начисления, каталог,
     * авторов и прогоны импортов. Отдельные удаления для этого не нужны, в схеме стоит
     * {@code ON DELETE CASCADE}.
     *
     * <p>Необратимо. Ключи маркетплейса придётся ввести заново, и данные тоже
     * загрузятся заново: с другим кабинетом они могут оказаться другими. Остальные
     * магазины пользователя при этом не затрагиваются.
     *
     * @return 204, если магазин был
     * @throws ru.analizer.web.AccountNotFoundException магазина нет или он не этот
     */
    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> disconnect(@PathVariable Long accountId) {
        accountLookup.deleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    private AccountInfo toInfo(SellerAccount account) {
        // Название платформы и её код читаются здесь, пока сессия транзакции ещё
        // открыта: возврат сущности наружу приводил бы к 500 на ленивом прокси.
        return toInfo(account, account.getMarketplace().getCode(),
                account.getMarketplace().getName());
    }

    private AccountInfo toInfo(Long accountId) {
        return toInfo(accountLookup.requireAccount(accountId));
    }

    private AccountInfo toInfo(SellerAccount account, String marketplaceCode,
                               String marketplaceName) {
        return new AccountInfo(
                account.getId(),
                marketplaceCode,
                marketplaceName,
                account.getName(),
                account.getClientId(),
                ozonProductRepository.countBySellerAccountId(account.getId()),
                importRunRepository.countBySellerAccountId(account.getId()));
    }

    /** @param marketplace код маркетплейса; тип создаваемого магазина
     *  @param clientId    идентификатор клиента
     *  @param apiKey      секретный ключ; в ответе не возвращается */
    public record CreateAccountRequest(String marketplace, String clientId, String apiKey) {
    }

    /** @param clientId идентификатор клиента маркетплейса
     *  @param apiKey   секретный ключ; в ответе не возвращается */
    public record CredentialsRequest(String clientId, String apiKey) {
    }

    /**
     * @param id              идентификатор магазина; подставляется в путь прочих запросов
     * @param marketplaceCode код платформы, на которой магазин работает
     * @param marketplaceName название платформы для интерфейса
     * @param shopName        название магазина; у двух магазинов одной платформы может
     *                        совпадать, поэтому в интерфейсе показывают рядом clientId
     * @param clientId        идентификатор клиента; секретный ключ не возвращается
     * @param catalogProducts товаров в каталоге; 0 означает, что каталог ещё не импортирован
     * @param importRuns      сколько импортов запускалось за всё время
     */
    public record AccountInfo(
            Long id,
            String marketplaceCode,
            String marketplaceName,
            String shopName,
            String clientId,
            long catalogProducts,
            long importRuns
    ) {
    }
}