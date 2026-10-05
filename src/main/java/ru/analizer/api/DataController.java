package ru.analizer.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.account.domain.Marketplace;
import ru.analizer.analytics.CatalogFacts;
import ru.analizer.account.domain.AccountLookup;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.persistence.repository.OzonProductRepository;
import ru.analizer.sync.application.DayStateService;
import ru.analizer.sync.domain.PeriodCoverage;

import java.time.LocalDate;
import java.util.List;

/**
 * Состояние наших данных: что уже загружено, а что нет.
 *
 * <p>Ни один метод этой группы не обращается к API маркетплейса. Ответ всегда отражает
 * содержимое базы на момент вызова, поэтому эти запросы можно делать часто и сколько
 * угодно раз — квота не тратится.
 *
 * <p>Смысл группы — не «получить отчёт», а «можно ли доверять отчёту». Ответ без
 * начислений и ответ «данных нет» выглядят одинаково, если не сказать, что произошло;
 * {@link PeriodCoverage} делает это различие явным.
 */
@RestController
@RequestMapping("/api/marketplaces/{marketplace}/data")
public class DataController {

    private final DayStateService dayStateService;
    private final AccountLookup accountLookup;
    private final CatalogFacts catalogFacts;
    private final OzonProductRepository productRepository;

    public DataController(DayStateService dayStateService,
                          AccountLookup accountLookup,
                          CatalogFacts catalogFacts,
                          OzonProductRepository productRepository) {
        this.dayStateService = dayStateService;
        this.accountLookup = accountLookup;
        this.catalogFacts = catalogFacts;
        this.productRepository = productRepository;
    }

    /**
     * GET /api/marketplaces/ozon/data/coverage?dateFrom=…&dateTo=…
     *
     * <p>Покрытие периода: сколько дней загружено, каких не хватает, какие ещё не
     * окончательные. Отвечает и до первой синхронизации — отсутствие данных это ответ,
     * а не ошибка.
     *
     * <p>Чтобы узнать про один день, передайте одинаковые {@code dateFrom} и
     * {@code dateTo}. Отдельного метода «про день» нет намеренно: он был бы тем же
     * запросом с другим числом параметров.
     *
     * @param dateFrom начало периода включительно
     * @param dateTo   конец периода включительно
     */
    @GetMapping("/coverage")
    public PeriodCoverage coverage(
            @PathVariable String marketplace,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }
        // Аккаунта может ещё не быть — это «данных нет», а не ошибка.
        Long accountId = accountLookup.findAccount(marketplace)
                .map(SellerAccount::getId)
                .orElse(null);
        return dayStateService.coverage(accountId, dateFrom, dateTo);
    }

    /**
     * GET /api/marketplaces/ozon/data/catalog
     *
     * <p>Состояние каталога товаров: сколько товаров и когда он обновлялся. Возвращает
     * количество, а не сами товары — чтобы посмотреть товары вместе с финансами, есть
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
    public record CatalogState(long products, java.time.Instant lastSyncedAt, boolean loaded) {
    }
}