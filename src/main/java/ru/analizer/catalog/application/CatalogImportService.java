package ru.analizer.catalog.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.account.repository.MarketplaceRepository;
import ru.analizer.account.repository.SellerAccountRepository;
import ru.analizer.catalog.domain.CatalogImportReport;
import ru.analizer.catalog.repository.OzonProductRepository;
import ru.analizer.integration.MarketplaceAdapter;
import ru.analizer.integration.ProductCatalogAdapter;
import ru.analizer.integration.model.ProductEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final ProductWriter writer;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final OzonProductRepository productRepository;

    public CatalogImportService(ProductCatalogAdapter catalogAdapter,
                                MarketplaceAdapter financeAdapter,
                                ProductWriter writer,
                                MarketplaceRepository marketplaceRepository,
                                SellerAccountRepository sellerAccountRepository,
                                OzonProductRepository productRepository) {
        this.catalogAdapter = catalogAdapter;
        this.writer = writer;
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.productRepository = productRepository;
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
        List<ProductEntry> products = new ArrayList<>(
                catalogAdapter.fetchAllProducts(account.credentials(), PAGE_LIMIT));

        // Архивные догружаем до подсчёта итогов, иначе они учитывались бы дважды:
        // обход их не видит, а в total уже вошли бы отдельной строкой ниже.
        Set<Long> fetched = products.stream()
                .map(ProductEntry::sku)
                .collect(Collectors.toSet());
        List<ProductEntry> archived = archivedProducts(account, fetched);
        products.addAll(archived);

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
        log.info("Каталог {}: сохранено {} из {} товаров, с ошибкой {}, из них архивных {}",
                account.getId(), saved, total, failed, archived.size());
        return CatalogImportReport.of(total, saved, failed);
    }

    /**
     * Товары, которых не оказалось в основном обходе, но по которым есть начисления.
     *
     * <p>Обход идёт с {@code visibility=ALL}, а это по документации OZON «все товары,
     * кроме архивных». Поэтому обычный товар сюда попасть не может: если SKU нет в
     * обходе, но есть в начислениях, значит он снят с продажи. Раньше такие товары
     * просто не показывались, и суммы отчёта по товарам расходились с дневным на всю
     * сумму их продаж — без единого слова о том, откуда взялась разница.
     *
     * <p>Список берётся после основного обхода и до записи, и поэтому требует фильтра.
     * Пока товары в базу не легли, запрос «что есть в начислениях, но нет в каталоге»
     * возвращает <b>все</b> SKU из начислений — включая те, что обход уже принёс: они
     * ещё не записаны, а запрос смотрит в таблицу. Без фильтра они сочлись бы
     * архивными, и, будучи дописаны в конец списка, перезаписали бы только что
     * сохранённые обычные товары. Заметнее всего это при первой загрузке, когда
     * каталога в базе нет вовсе; при повторной плашки лишние не появляются, потому
     * что товары уже на месте и в запрос не попадают.
     *
     * <p>Пустой результат — обычное дело, а не сбой: у аккаунта просто нет таких
     * товаров. Именно поэтому догрузка не является отдельной задачей и не влияет на
     * счётчики прогресса.
     *
     * @param fetched SKU, уже принесённые обходом каталога; они точно не архивные
     */
    private List<ProductEntry> archivedProducts(SellerAccount account, Set<Long> fetched) {
        List<Long> missing = productRepository.skusMissingFromCatalog(account.getId()).stream()
                // Обход принёс товар — значит он доступен к продаже, иначе его не было бы
                // среди «всех, кроме архивных». Помечать его архивным нельзя.
                .filter(sku -> !fetched.contains(sku))
                .toList();
        if (missing.isEmpty()) {
            return List.of();
        }
        List<String> skus = missing.stream().map(String::valueOf).toList();
        List<ProductEntry> archived = catalogAdapter.fetchArchivedBySku(account.credentials(), skus);
        if (archived.size() < missing.size()) {
            // Не все SKU нашлись даже точечно. Это бывает, когда товар удалён насовсем,
            // а не убран в архив, и такие SKU в отчёте остаются отдельным списком —
            // молчать о них нельзя, иначе расхождение сумм необъяснимо.
            log.info("Каталог {}: из {} отсутствующих SKU архивными оказались {}, остальные не найдены нигде",
                    account.getId(), missing.size(), archived.size());
        }
        return archived;
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