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
 * Поиск маркетплейса и аккаунта текущего пользователя.
 *
 * <p><b>Здесь обеспечивается изоляция данных.</b> Все контроллеры получают аккаунт
 * только через этот класс, а все запросы к начислениям и каталогу идут по
 * {@code sellerAccountId}. Поэтому пользователь не может увидеть чужие данные:
 * подменить идентификатор аккаунта в запросе негде — его там просто нет.
 *
 * <p>Раньше идентификатор приходил параметром {@code clientId}, и это была
 * протекающая абстракция: {@code clientId} — понятие OZON, у Wildberries это
 * {@code companyId}. Теперь аккаунт определяется пользователем и маркетплейсом.
 *
 * <p>Аккаунт на маркетплейс ровно один: ограничение в базе
 * ({@code uq_seller_account_user_marketplace}), а не в коде.
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
     * Аккаунт текущего пользователя на маркетплейсе.
     *
     * <p>Пусто — это не ошибка, а «маркетплейс ещё не подключён». Именно поэтому метод
     * возвращает Optional, а не бросает исключение: отчёт об ещё не загруженных
     * данных обязан ответить {@code NOT_LOADED}, а не упасть.
     */
    public Optional<SellerAccount> findAccount(String marketplaceCode) {
        AppUser user = requireUser();
        Marketplace marketplace = requireMarketplace(marketplaceCode);
        return sellerAccountRepository.findByUserIdAndMarketplaceId(user.getId(), marketplace.getId());
    }

    /**
     * Аккаунт текущего пользователя, либо отказ.
     *
     * <p>Используется там, где без аккаунта работать нечего: запуск импорта и выборка
     * данных. Отличается от {@link #findAccount} тем, что не подсказывает причину —
     * просто «маркетплейс не подключён», без SQL-подробностей.
     *
     * @throws ru.analizer.web.NotConnectedException маркетплейс ещё не подключён
     */
    public SellerAccount requireAccount(String marketplaceCode) {
        return findAccount(marketplaceCode)
                .orElseThrow(() -> new ru.analizer.web.NotConnectedException(
                        requireMarketplace(marketplaceCode).getCode()));
    }

    /**
     * Аккаунт текущего пользователя по идентификатору.
     *
     * <p>Идентификатор приходит из фоновой задачи, поэтому проверка принадлежности
     * обязательна: без неё запрос, подделавший {@code accountId}, прочитал бы чужие
     * начисления.
     *
     * @throws ru.analizer.web.NotConnectedException аккаунт не принадлежит текущему пользователю
     */
    public SellerAccount requireAccount(Long accountId) {
        AppUser user = requireUser();
        SellerAccount account = findAccount(accountId)
                .orElseThrow(() -> new ru.analizer.web.NotConnectedException(Long.toString(accountId)));
        if (!account.getUser().getId().equals(user.getId())) {
            // Намеренно одинаковый ответ: различая «нет такого» и «не твой»,
            // мы пересказываем злоумышленнику, какие аккаунты существуют.
            throw new ru.analizer.web.NotConnectedException(Long.toString(accountId));
        }
        return account;
    }

    public Optional<SellerAccount> findAccount(Long accountId) {
        return sellerAccountRepository.findById(accountId);
    }

    /**
     * Аккаунт, созданный или обновлённый, с уже прочитанными полями.
     *
     * <p>Отдельная запись вместо самой сущности: контроллеру нужен код маркетплейса, а
     * читать его на ленивом прокси вне транзакции нельзя — запрос упал бы с 500. Здесь
     * сессия ещё открыта, и всё, что нужно наружу, извлекается один раз.
     *
     * @param marketplaceCode код маркетплейса в верхнем регистре
     * @param clientId        подключённый идентификатор клиента
     */
    public record ConnectedAccount(String marketplaceCode, String clientId) {
    }

    /**
     * Аккаунт, при необходимости созданный, с проверенными реквизитами.
     *
     * <p>Используется только подключением маркетплейса: на чистой установке аккаунта
     * ещё нет, и именно это действие должно его завести. Импорт аккаунт только читает.
     *
     * @param credentials реквизиты от пользователя; если аккаунт уже есть, обновляются
     */
    @Transactional
    public ConnectedAccount ensureAccount(String marketplaceCode,
                                           MarketplaceCredentials credentials) {
        AppUser user = requireUser();
        Marketplace marketplace = requireMarketplace(marketplaceCode);

        Optional<SellerAccount> existing = sellerAccountRepository
                .findByUserIdAndMarketplaceId(user.getId(), marketplace.getId());
        SellerAccount account;
        if (existing.isPresent()) {
            existing.get().updateCredentials(credentials.clientId(), credentials.apiKey());
            account = sellerAccountRepository.save(existing.get());
        } else {
            account = provisionerFor(marketplace.getCode())
                    .provision(marketplace, user, credentials);
        }

        // Код читается здесь, пока сессия открыта: возврат сущности наружу приводил бы к
        // 500 при попытке открыть ленивый прокси за пределами транзакции.
        return new ConnectedAccount(marketplace.getCode(), account.getClientId());
    }

    private MarketplaceProvisioner provisionerFor(String code) {
        return provisioners.stream()
                .filter(p -> p.supports(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Для маркетплейса " + code + " не реализовано подключение"));
    }
}