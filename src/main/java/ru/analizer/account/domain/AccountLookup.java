package ru.analizer.account.domain;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.account.repository.MarketplaceRepository;
import ru.analizer.account.repository.SellerAccountRepository;
import ru.analizer.auth.domain.AppUser;
import ru.analizer.auth.repository.AppUserRepository;
import ru.analizer.web.UnknownMarketplaceException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Поиск маркетплейса и магазина текущего пользователя.
 *
 * <p><b>Здесь обеспечивается изоляция данных.</b> Все контроллеры получают магазин
 * только через этот класс, а все запросы к начислениям и каталогу идут по
 * {@code sellerAccountId}.
 *
 * <p>Магазина на маркетплейсе может быть сколько угодно: у продавца несколько
 * кабинетов OZON, и у каждого свои начисления и каталог. Поэтому идентификатор
 * магазина приходит из пути запроса — {@code /api/accounts/{accountId}/...} — и
 * принадлежность проверяется в каждом обращении.
 *
 * <p>Ответ на «нет такого магазина» и на «магазин чужой» намеренно одинаковый:
 * различая их, мы пересказывали бы, какие магазины вообще существуют.
 *
 * <p>Раньше магазин определялся пользователем и маркетплейсом, а идентификатор был
 * вовсе не в запросе. Это работало, пока магазин на платформе был один. Параметр
 * {@code clientId}, как и раньше, в контракт не возвращается: это понятие OZON, у
 * Wildberries оно называется {@code companyId}.
 */
@Component
public class AccountLookup {

    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final AppUserRepository appUserRepository;
    private final List<MarketplaceProvisioner> provisioners;

    public AccountLookup(MarketplaceRepository marketplaceRepository,
                         SellerAccountRepository sellerAccountRepository,
                         AppUserRepository appUserRepository,
                         List<MarketplaceProvisioner> provisioners) {
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.appUserRepository = appUserRepository;
        this.provisioners = provisioners;
    }

    /**
     * Маркетплейс по коду из пути запроса, регистр не важен.
     *
     * @throws UnknownMarketplaceException такого маркетплейса нет
     */
    public Marketplace requireMarketplace(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Не указан маркетплейс в пути запроса");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        return marketplaceRepository.findByCode(normalized)
                .orElseThrow(() -> new UnknownMarketplaceException(normalized));
    }

    /** Пользователь текущего запроса. */
    public AppUser requireUser() {
        return appUserRepository.findByLogin(CurrentUser.login())
                .orElseThrow(() -> new IllegalStateException(
                        "Сессия указывает на несуществующего пользователя: " + CurrentUser.login()));
    }

    /**
     * Магазины текущего пользователя.
     *
     * <p>Пустой список — это не ошибка, а «ничего не подключено»: именно поэтому
     * возвращается просто список, а не бросается исключение.
     */
    public List<SellerAccount> findAccounts() {
        return sellerAccountRepository.findOwnedWithMarketplace(requireUser().getId());
    }

    /**
     * Магазин текущего пользователя по идентификатору, либо отказ.
     *
     * <p>Идентификатор приходит из пути запроса, поэтому проверка принадлежности
     * обязательна: без неё запрос, подставивший чужой {@code accountId}, прочитал бы
     * чужие начисления. Проверка входит в сам запрос
     * ({@code findByIdAndUser_Id}), а не делается сравнением потом.
     *
     * @throws ru.analizer.web.NotConnectedException магазина нет или он не этот
     */
    public SellerAccount requireAccount(Long accountId) {
        if (accountId == null) {
            throw new ru.analizer.web.AccountNotFoundException("не указан в пути запроса");
        }
        return sellerAccountRepository
                .findOwnedWithMarketplace(accountId, requireUser().getId())
                .orElseThrow(() -> new ru.analizer.web.AccountNotFoundException(
                        Long.toString(accountId)));
    }

    /**
     * Магазин, созданный или обновлённый, с уже прочитанными полями.
     *
     * <p>Отдельная запись вместо самой сущности: контроллеру нужен код маркетплейса, а
     * читать его на ленивом прокси вне транзакции нельзя — запрос упал бы с 500. Здесь
     * сессия ещё открыта, и всё, что нужно наружу, извлекается один раз.
     *
     * @param id              идентификатор магазина; подставляется в путь следующих запросов
     * @param marketplaceCode код маркетплейса в верхнем регистре
     * @param clientId        подключённый идентификатор клиента
     */
    public record ConnectedAccount(Long id, String marketplaceCode, String clientId) {
    }

    /**
     * Новый магазин пользователя на маркетплейсе.
     *
     * <p>Используется только подключением: на чистой установке магазина ещё нет, и
     * именно это действие его заводит. Импорт магазин только читает.
     *
     * <p>Повторное подключение того же кабинета запрещено: уникальное ограничение в
     * базе не даст создать дубль, а заранее найденный аккаунт позволяет ответить
     * внятной ошибкой. Смена ключей уже существующего магазина — отдельное действие,
     * {@link #updateCredentials}.
     *
     * @throws ru.analizer.web.ShopAlreadyConnectedException этот кабинет уже подключён
     */
    @Transactional
    public ConnectedAccount createAccount(String marketplaceCode,
                                           MarketplaceCredentials credentials) {
        AppUser user = requireUser();
        Marketplace marketplace = requireMarketplace(marketplaceCode);

        Optional<SellerAccount> sameShop = sellerAccountRepository
                .findOwnedWithMarketplace(
                        user.getId(), marketplace.getId(), credentials.clientId());
        if (sameShop.isPresent()) {
            throw new ru.analizer.web.ShopAlreadyConnectedException(
                    marketplace.getCode(), credentials.clientId());
        }

        SellerAccount account = provisionerFor(marketplace.getCode())
                .provision(marketplace, user, credentials);

        // Код читается здесь, пока сессия открыта: возврат сущности наружу приводил бы к
        // 500 при попытке открыть ленивый прокси за пределами транзакции.
        return new ConnectedAccount(account.getId(), marketplace.getCode(), account.getClientId());
    }

    /**
     * Замена реквизитов существующего магазина.
     *
     * <p>Отдельное действие от подключения: смена ключей не заводит новый магазин, а
     * чинит тот, что уже есть. Иначе пришлось бы различать эти два случая по
     * наличию идентификатора в пути, а это ровно то различие, которое нельзя
     * додумывать на клиенте.
     *
     * <p>Название обновляется вместе с ключами: это единственный момент, когда
     * пользователь сам подтверждает реквизиты, и единственная возможность починить
     * название у магазина, заведённого до появления настоящих имён. Без этого такие
     * магазины остались бы с техническим идентификатором навсегда — переименовать их
     * можно было бы только удалением вместе с нагрузкой.
     */
    @Transactional
    public ConnectedAccount updateCredentials(Long accountId, MarketplaceCredentials credentials) {
        SellerAccount account = requireAccount(accountId);
        account.updateCredentials(credentials.clientId(), credentials.apiKey());
        account.updateName(provisionerFor(account.getMarketplace().getCode())
                .resolveShopName(account.getMarketplace(), credentials));
        sellerAccountRepository.save(account);
        return new ConnectedAccount(account.getId(),
                account.getMarketplace().getCode(), account.getClientId());
    }

    /**
     * Удаляет магазин пользователя вместе со всеми его данными.
     *
     * <p>Отдельные удаления не нужны: в схеме все таблицы ссылаются на
     * {@code seller_account} с {@code ON DELETE CASCADE}, поэтому начисления, каталог,
     * авторы и прогоны уходят вместе с аккаунтом. Справочник типов начислений остаётся:
     * он общий для маркетплейса, а не для магазина.
     *
     * <p>Магазин ищется по текущему пользователю, поэтому чужой удалить нельзя даже
     * зная его идентификатор.
     *
     * @throws ru.analizer.web.AccountNotFoundException магазина нет или он не этот
     */
    @Transactional
    public void deleteAccount(Long accountId) {
        sellerAccountRepository.delete(requireAccount(accountId));
    }

    private MarketplaceProvisioner provisionerFor(String code) {
        return provisioners.stream()
                .filter(p -> p.supports(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Для маркетплейса " + code + " не реализовано подключение"));
    }
}