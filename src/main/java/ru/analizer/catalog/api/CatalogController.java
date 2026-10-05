package ru.analizer.catalog.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.account.domain.AccountLookup;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.analytics.infrastructure.CatalogFacts;
import ru.analizer.catalog.repository.OzonProductRepository;

import java.time.Instant;
import java.util.List;

/**
 * Состояние каталога товаров.
 *
 * <p>Живёт в {@code catalog}, а не в общем «состоянии данных»: оба ответа считаются по
 * каталогу, а покрытие периода — по загруженным дням, и смешивать их в одном
 * контроллере значило бы держать здесь зависимости от обеих фич.
 */
@RestController
@RequestMapping("/api/marketplaces/{marketplace}/data")
public class CatalogController {

    private final AccountLookup accountLookup;
    private final CatalogFacts catalogFacts;
    private final OzonProductRepository productRepository;

    public CatalogController(AccountLookup accountLookup,
                             CatalogFacts catalogFacts,
                             OzonProductRepository productRepository) {
        this.accountLookup = accountLookup;
        this.catalogFacts = catalogFacts;
        this.productRepository = productRepository;
    }

    /**
     * GET /api/marketplaces/ozon/data/catalog
     *
     * <p>Сколько товаров и когда каталог обновлялся. Возвращает количество, а не сами
     * товары — чтобы посмотреть товары вместе с финансами, есть
     * {@code /analytics/products}.
     *
     * <p>{@code loaded = false} при {@code products = 0} означает «каталог ещё ни разу не
     * импортировался», а не «у продавца нет товаров».
     */
    @GetMapping("/catalog")
    public CatalogState catalog(@PathVariable String marketplace) {
        SellerAccount account = accountLookup.requireAccount(marketplace);
        long products = productRepository.countBySellerAccountId(account.getId());
        return new CatalogState(products, catalogFacts.lastCatalogSync(account.getId()), products > 0);
    }

    /**
     * GET /api/marketplaces/ozon/data/authors
     *
     * <p>Варианты авторов для подсказок фильтра. Список строится из того, что реально
     * ввёл продавец, поэтому может содержать странные значения: если продавец написал
     * вместо имени инициалы, подсказка повторит инициалы.
     *
     * <p>Имена уже сведены к виду «фамилия инициалы» в нижнем регистре, поэтому их можно
     * напрямую передавать в параметр {@code author} отчёта по товарам. В самом отчёте
     * авторы показываются исходным текстом продавца.
     */
    @GetMapping("/authors")
    public List<String> authors(@PathVariable String marketplace) {
        SellerAccount account = accountLookup.requireAccount(marketplace);
        return catalogFacts.authorKeys(account.getId());
    }

    /**
     * @param products     товаров в каталоге
     * @param lastSyncedAt когда каталог обновлялся в последний раз
     * @param loaded       {@code false}, если импорта каталога ещё не было
     */
    public record CatalogState(long products, Instant lastSyncedAt, boolean loaded) {
    }
}