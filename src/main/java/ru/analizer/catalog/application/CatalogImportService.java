package ru.analizer.catalog.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.analizer.catalog.domain.CatalogImportReport;
import ru.analizer.integration.MarketplaceAdapter;
import ru.analizer.integration.ProductCatalogAdapter;
import ru.analizer.integration.model.ProductEntry;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.account.repository.MarketplaceRepository;
import ru.analizer.account.repository.SellerAccountRepository;

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
public class CatalogImportService {

    /** Максимум по спецификации метода. */
    private static final int PAGE_LIMIT = 1000;

    private static final Logger log = LoggerFactory.getLogger(CatalogImportService.class);

    private final ProductCatalogAdapter catalogAdapter;
    private final MarketplaceAdapter financeAdapter;
    private final ProductWriter writer;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;

    public CatalogImportService(ProductCatalogAdapter catalogAdapter,
                              MarketplaceAdapter financeAdapter,
                              ProductWriter writer,
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
    public CatalogImportReport importProducts(Long accountId, CatalogImportProgressListener progress) {
        SellerAccount account = requireAccount(accountId);
        List<ProductEntry> products = catalogAdapter.fetchAllProducts(account.credentials(), PAGE_LIMIT);

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
        return CatalogImportReport.of(total, saved, failed);
    }

    /**
     * Аккаунт по идентификатору.
     *
     * <p>Идентификатор приходит из пути запроса, а не из параметра клиента.
     */
    private SellerAccount requireAccount(Long accountId) {
        return sellerAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException("Аккаунт " + accountId + " не найден"));
    }
}