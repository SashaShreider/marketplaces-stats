package ru.analizer.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.marketplace.MarketplaceAdapter;
import ru.analizer.marketplace.ozon.OzonProperties;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.OzonProductRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;
import ru.analizer.persistence.repository.ImportRunRepository;

import java.util.List;

/**
 * Что вообще доступно.
 *
 * <p>Единственный метод без сегмента маркетплейса: он отвечает на вопрос «какие
 * маркетплейсы подключены и готовы ли они к работе», поэтому вызывается до того, как
 * известен конкретный маркетплейс.
 *
 * <p>Собственные запросы к OZON не выполняет: только состояние конфигурации и нашей базы.
 */
@RestController
@RequestMapping("/api/marketplaces")
public class MarketplaceController {

    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final ImportRunRepository importRunRepository;
    private final OzonProductRepository ozonProductRepository;
    private final MarketplaceAdapter marketplaceAdapter;
    private final OzonProperties ozonProperties;

    public MarketplaceController(MarketplaceRepository marketplaceRepository,
                                 SellerAccountRepository sellerAccountRepository,
                                 ImportRunRepository importRunRepository,
                                 OzonProductRepository ozonProductRepository,
                                 MarketplaceAdapter marketplaceAdapter,
                                 OzonProperties ozonProperties) {
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.importRunRepository = importRunRepository;
        this.ozonProductRepository = ozonProductRepository;
        this.marketplaceAdapter = marketplaceAdapter;
        this.ozonProperties = ozonProperties;
    }

    /**
     * GET /api/marketplaces
     *
     * <p>Позволяет клиенту построить выбор маркетплейса, не зная заранее кодов.
     * Поле {@code credentialsConfigured} показывает, можно ли запускать импорт: без
     * ключей запрос вернёт 503, и клиенту лучше сказать это заранее.
     */
    @GetMapping
    public List<MarketplaceInfo> list() {
        return marketplaceRepository.findAll().stream()
                .map(this::toInfo)
                .toList();
    }

    private MarketplaceInfo toInfo(Marketplace marketplace) {
        List<SellerAccount> accounts = sellerAccountRepository
                .findByMarketplaceId(marketplace.getId());
        SellerAccount account = accounts.isEmpty() ? null : accounts.getFirst();

        boolean credentialsConfigured = marketplaceAdapter.marketplaceCode().equals(marketplace.getCode())
                && ozonProperties.isConfigured();

        return new MarketplaceInfo(
                marketplace.getCode(),
                marketplace.getName(),
                accounts.size(),
                account == null ? null : account.getName(),
                credentialsConfigured,
                account == null ? 0 : ozonProductRepository.countBySellerAccountId(account.getId()),
                account == null ? 0 : importRunRepository.countBySellerAccountId(account.getId()));
    }

    /**
     * @param code                   код маркетплейса; подставляется в путь следующих запросов
     * @param accounts               сколько на него заведено аккаунтов; больше одного — конфликт
     * @param credentialsConfigured  заданы ли ключи API; при {@code false} импорт вернёт 503
     * @param catalogProducts        товаров в каталоге; 0 означает, что каталог ещё не импортирован
     * @param importRuns             сколько импортов запускалось за всё время
     */
    public record MarketplaceInfo(
            String code,
            String name,
            int accounts,
            String accountName,
            boolean credentialsConfigured,
            long catalogProducts,
            long importRuns
    ) {
    }
}