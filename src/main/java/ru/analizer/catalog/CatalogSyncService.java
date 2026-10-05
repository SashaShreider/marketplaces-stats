package ru.analizer.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.analizer.marketplace.MarketplaceAdapter;
import ru.analizer.marketplace.ProductCatalogAdapter;
import ru.analizer.marketplace.ProductEntry;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;

import java.util.List;

/**
 * Загрузка каталога товаров.
 *
 * <p>В отличие от финансовой синхронизации у каталога нет договорённости «догружаем
 * только недостающее»: OZON не сообщает, какие товары изменились, а перебор даже
 * 100 товаров стоит нескольких запросов. Поэтому каталог каждый раз переписывается
 * целиком — зато он не может разойтись с кабинетом.
 */
@Service
public class CatalogSyncService {

    /** Максимум по спецификации метода. */
    private static final int PAGE_LIMIT = 1000;

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);

    private final ProductCatalogAdapter catalogAdapter;
    private final MarketplaceAdapter financeAdapter;
    private final CatalogWriter writer;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;

    public CatalogSyncService(ProductCatalogAdapter catalogAdapter,
                              MarketplaceAdapter financeAdapter,
                              CatalogWriter writer,
                              MarketplaceRepository marketplaceRepository,
                              SellerAccountRepository sellerAccountRepository) {
        this.catalogAdapter = catalogAdapter;
        this.financeAdapter = financeAdapter;
        this.writer = writer;
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
    }

    /**
     * Загружает все товары аккаунта.
     *
     * <p>Каждый товар пишется своей транзакцией, поэтому прерванная загрузка не теряет
     * то, что уже записала.
     *
     * @param progress необязательный получатель прогресса для фоновой задачи
     */
    public CatalogSyncReport sync(String clientId, CatalogProgressListener progress) {
        SellerAccount account = resolveAccount(clientId);
        List<ProductEntry> products = catalogAdapter.fetchAllProducts(PAGE_LIMIT);

        int total = products.size();
        if (progress != null) {
            progress.onStart(total);
        }

        int saved = 0;
        int failed = 0;
        for (ProductEntry product : products) {
            try {
                writer.persist(account, product);
                saved++;
                if (progress != null) {
                    progress.onProduct(saved + failed, total, product.sku());
                }
            } catch (RuntimeException e) {
                // Один несохранённый товар не должен обесценить остальные: иначе
                // пришлось бы начинать заново после каждой неудачи.
                failed++;
                log.warn("Товар {} не сохранён: {}", product.sku(), e.getMessage());
                if (progress != null) {
                    progress.onProductFailed(product.sku(), e);
                }
            }
        }
        log.info("Каталог {}: сохранено {} из {} товаров, с ошибкой {}", account.getId(), saved, total, failed);
        return CatalogSyncReport.of(total, saved, failed);
    }

    private SellerAccount resolveAccount(String clientId) {
        String code = financeAdapter.marketplaceCode();
        Marketplace marketplace = marketplaceRepository.findByCode(code)
                .orElseThrow(() -> new IllegalStateException(
                        "Маркетплейс " + code + " не найден в таблице marketplace"));
        return sellerAccountRepository
                .findByMarketplaceIdAndClientId(marketplace.getId(), clientId)
                .orElseGet(() -> sellerAccountRepository.save(
                        new SellerAccount(marketplace, code + " " + clientId, clientId)));
    }
}