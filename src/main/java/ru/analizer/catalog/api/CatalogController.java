package ru.analizer.catalog.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
@RequestMapping("/api/accounts/{accountId}/data")
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
     * GET /api/accounts/1/data/catalog
     *
     * <p>Сколько товаров и когда каталог обновлялся. Возвращает количество, а не сами
     * товары — чтобы посмотреть товары вместе с финансами, есть
     * {@code /analytics/products}.
     *
     * <p>{@code loaded = false} при {@code products = 0} означает «каталог ещё ни разу не
     * импортировался», а не «у продавца нет товаров».
     *
     * @throws ru.analizer.web.AccountNotFoundException магазина нет или он не этот
     */
    @GetMapping("/catalog")
    public CatalogState catalog(@PathVariable Long accountId) {
        SellerAccount account = accountLookup.requireAccount(accountId);
        long products = productRepository.countBySellerAccountId(account.getId());
        return new CatalogState(products, catalogFacts.lastCatalogSync(account.getId()), products > 0);
    }

    /**
     * GET /api/accounts/1/data/authors
     *
     * <p>Варианты авторов для подсказок фильтра. Список строится из того, что реально
     * ввёл продавец, поэтому может содержать странные значения: если продавец написал
     * вместо имени инициалы, подсказка повторит инициалы.
     *
     * <p>Список отдаётся теми же строками, по которым идёт фильтр {@code author}, —
     * выбранное значение можно передать в отчёт по товарам как есть, и оно найдёт
     * те же товары. Поэтому рядом могут стоять «Сурцуков А.», «Сурцуков Анатолий» и
     * «А.В. Сурцуков»: это три написания одного человека, и свести их к одному может
     * только продавец, в карточках товара.
     *
     * <p>Список строится по каталогу конкретного магазина: у второго кабинета того же
     * продавца авторы свои.
     *
     * @throws ru.analizer.web.AccountNotFoundException магазина нет или он не этот
     */
    @GetMapping("/authors")
    public List<String> authors(@PathVariable Long accountId) {
        SellerAccount account = accountLookup.requireAccount(accountId);
        return catalogFacts.authorValues(account.getId());
    }

    /**
     * GET /api/accounts/1/data/products?query=летчик&limit=20
     *
     * <p>Товары для подсказки в фильтре «Обзора». Не отчёт: ни периода, ни денег,
     * ни страниц — только то, что нужно, чтобы выбрать товар и подставить его SKU
     * в отчёт.
     *
     * <p>Ограничение размера задано здесь, а не в запросе: подсказка, отдавшая
     * тысячу строк, всё равно не поместится на экран, а база потратит на неё
     * время. Пустой каталог даёт пустой список — это ответ, а не ошибка.
     *
     * @param query подстрока названия, артикула или ISBN
     * @param limit желаемое число строк; приводится к диапазону 1…50
     */
    @GetMapping("/products")
    public List<CatalogFacts.ProductOption> products(@PathVariable Long accountId,
                                                     @RequestParam(required = false) String query,
                                                     @RequestParam(defaultValue = "20") int limit) {
        SellerAccount account = accountLookup.requireAccount(accountId);
        return catalogFacts.productOptions(account.getId(), query, Math.clamp(limit, 1, 50));
    }

    /**
     * @param products     товаров в каталоге
     * @param lastSyncedAt когда каталог обновлялся в последний раз
     * @param loaded       {@code false}, если импорта каталога ещё не было
     */
    public record CatalogState(long products, Instant lastSyncedAt, boolean loaded) {
    }
}