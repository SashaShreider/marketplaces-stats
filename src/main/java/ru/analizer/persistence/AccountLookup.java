package ru.analizer.persistence;

import org.springframework.stereotype.Component;
import ru.analizer.marketplace.MarketplaceProvisioner;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;
import ru.analizer.sync.ImportConflictException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Поиск маркетплейса и его аккаунта.
 *
 * <p>Живёт рядом с сущностями, а не в API, потому что нужен и контроллерам, и сервисам:
 * раньше каждый из них искал аккаунт по-разному.
 *
 * <p>Аккаунт на маркетплейс ровно один — это ограничение схемы, а не недоработка.
 * Если аккаунтов окажется больше, выбирать один молча нельзя: пришлось бы отдать данные
 * не того продавца. Поэтому такое состояние считается ошибкой настройки.
 *
 * <p>Раньше идентификатор аккаунта приходил параметром {@code clientId}, и это была
 * протекающая абстракция: {@code clientId} — понятие OZON, у Wildberries это
 * {@code companyId}. Клиент API больше не знает, как маркетплейс называет своего
 * продавца.
 */
@Component
public class AccountLookup {

    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final List<MarketplaceProvisioner> provisioners;

    public AccountLookup(MarketplaceRepository marketplaceRepository,
                         SellerAccountRepository sellerAccountRepository,
                         List<MarketplaceProvisioner> provisioners) {
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.provisioners = provisioners;
    }

    /**
     * Маркетплейс по коду из пути запроса, регистр не важен.
     *
     * @throws UnknownMarketplaceException такого маркетплейса нет
     */
    public Marketplace requireMarketplace(String code) {
        return findMarketplace(code).orElseThrow(() -> new UnknownMarketplaceException(normalize(code)));
    }

    public Optional<Marketplace> findMarketplace(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return marketplaceRepository.findByCode(normalize(code));
    }

    /**
     * Единственный аккаунт маркетплейса.
     *
     * @throws IllegalArgumentException аккаунтов нет
     * @throws ImportConflictException   аккаунтов больше одного
     */
    public SellerAccount requireAccount(String marketplaceCode) {
        Optional<SellerAccount> account = findAccount(marketplaceCode);
        return account.orElseThrow(() -> new IllegalArgumentException(
                "На маркетплейс " + normalize(marketplaceCode) + " не заведен ни один аккаунт"));
    }

    /**
     * Аккаунт, если он есть.
     *
     * <p>Пусто — это не ошибка, а «данных ещё нет». Именно поэтому метод возвращает
     * Optional, а не бросает исключение: отчёт об ещё не загруженных данных обязан
     * ответить {@code NOT_LOADED}, а не упасть.
     *
     * @throws ImportConflictException аккаунтов больше одного — выбрать молча нельзя
     */
    public Optional<SellerAccount> findAccount(String marketplaceCode) {
        Marketplace marketplace = requireMarketplace(marketplaceCode);
        List<SellerAccount> accounts = sellerAccountRepository
                .findByMarketplaceId(marketplace.getId());
        if (accounts.size() > 1) {
            throw ImportConflictException.ambiguousAccount(accounts.size(), marketplace.getCode());
        }
        return accounts.isEmpty() ? Optional.empty() : Optional.of(accounts.getFirst());
    }

    public Optional<SellerAccount> findAccount(Long accountId) {
        return sellerAccountRepository.findById(accountId);
    }

    /**
     * Аккаунт маркетплейса, при необходимости созданный.
     *
     * <p>Используется только запуском импорта. На чистой установке аккаунта ещё нет, и
     * без его создания первый же импорт упал бы — а именно он и должен приводить систему
     * в рабочее состояние.
     *
     * <p>Читающие методы сюда не ходят: для них отсутствие аккаунта — это «данных нет»,
     * а не повод что-то создавать.
     *
     * @throws ImportConflictException аккаунтов больше одного — выбрать молча нельзя
     */
    public SellerAccount ensureAccount(String marketplaceCode) {
        Marketplace marketplace = requireMarketplace(marketplaceCode);
        List<SellerAccount> accounts = sellerAccountRepository
                .findByMarketplaceId(marketplace.getId());
        if (accounts.size() > 1) {
            throw ImportConflictException.ambiguousAccount(accounts.size(), marketplace.getCode());
        }
        if (accounts.size() == 1) {
            return accounts.getFirst();
        }
        String code = marketplace.getCode();
        MarketplaceProvisioner provisioner = provisioners.stream()
                .filter(p -> p.supports(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Для маркетплейса " + code + " не настроено создание аккаунта"));
        return provisioner.provision(marketplace);
    }

    private static String normalize(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }
}