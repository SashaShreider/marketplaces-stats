package ru.analizer.analytics.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.analizer.account.domain.AccountLookup;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.analytics.application.DailyAnalyticsService;
import ru.analizer.analytics.application.ProductAnalyticsService;
import ru.analizer.analytics.domain.DailyReport;
import ru.analizer.analytics.domain.ProductReport;
import ru.analizer.analytics.domain.ReportScope;
import ru.analizer.analytics.domain.SalesDynamicsReport;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Отчёты по сохранённым данным одного магазина.
 *
 * <p>Ни один метод не обращается к API маркетплейса: отчёты считаются по нашей базе.
 * Поэтому их можно вызывать часто и без риска исчерпать квоту.
 *
 * <p>Каждый отчёт всегда сообщает, можно ли ему доверять, через поля {@code status} и
 * {@code coverage}. Нули за незагруженный день неотличимы от нулей за день без
 * начислений, и без этих полей пользователь решил бы, что денег не было.
 *
 * <p>Магазин определяется идентификатором из пути. Раньше вместо него был код
 * маркетплейса, и это работало, пока магазин на платформе был один: с несколькими
 * кабинетами одного продавца выбор стал бы неоднозначным, а молча взять не тот
 * значило бы показать чужие данные.
 */
@RestController
@RequestMapping("/api/accounts/{accountId}/analytics")
public class AnalyticsController {

    private final DailyAnalyticsService dailyAnalytics;
    private final ProductAnalyticsService productAnalytics;
    private final AccountLookup accountLookup;

    public AnalyticsController(DailyAnalyticsService dailyAnalytics,
                               ProductAnalyticsService productAnalytics,
                               AccountLookup accountLookup) {
        this.dailyAnalytics = dailyAnalytics;
        this.productAnalytics = productAnalytics;
        this.accountLookup = accountLookup;
    }

    /**
     * GET /api/accounts/1/analytics/daily?dateFrom=…&dateTo=…
     *
     * <p>Разбивка доходов, расходов и выплаты по дням периода, с детализацией расходов
     * по типам начислений.
     *
     * @param dateFrom начало периода включительно
     * @param dateTo   конец периода включительно
     * @throws ru.analizer.web.AccountNotFoundException магазина нет или он не этот
     */
    @GetMapping("/daily")
    public DailyReport dailyReport(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) List<Long> skus) {

        // Магазин ищется здесь, в потоке запроса: только тут доступна сессия. Сервис
        // получает его готовым и потому одинаково работает из фоновой задачи и из
        // теста — там, где сессии нет вовсе.
        SellerAccount account = accountLookup.requireAccount(accountId);
        // Товары сильнее автора: выбрав товар, пользователь уже ответил, чей он.
        // Подробнее — в ReportScope.
        ReportScope scope = skus != null && !skus.isEmpty()
                ? ReportScope.ofSkus(skus)
                : ReportScope.ofAuthor(author);
        return dailyAnalytics.dailyReport(
                account.getMarketplace().getCode(), Optional.of(account.getId()),
                dateFrom, dateTo, scope);
    }

    /**
     * GET /api/accounts/1/analytics/products?dateFrom=…&dateTo=…
     *
     * <p>Все товары каталога вместе с их продажами и списаниями за период.
     *
     * <p>Строки — все товары, а не только проданные. Ноль у товара, который есть в
     * каталоге, — честный ответ «продаж не было»; если же период загружен не полностью,
     * это указано в {@code status} и {@code coverage}, и нулям верить нельзя.
     *
     * <p>Фильтр {@code author} сравнивается точным совпадением с тем, как автор назван
     * в карточке товара, поэтому «Сурцуков А.» не найдёт «Сурцуков Анатолий»: ищется
     * ровно введённая строка. Исключение одно — если в карточке перечислено несколько
     * авторов, то найдёт любой из них: «Черняев А. Ю., Ланде А. А.» разбирается на
     * два значения, и достаточно назвать одно из них.
     *
     * <p>Подсказки для этого поля — {@code GET /data/authors}: там лежат ровно те
     * строки, которые найдут товары, поэтому безопаснее выбирать из списка, а не
     * вводить от руки.
     *
     * @param author автор ровно в том написании, в каком он указан в карточке товара
     * @param query  подстрока названия, своего артикула или ISBN
     * @param sort   критерий порядка строк: INCOME (по умолчанию), NAME, SKU, SOLD,
     *               EXPENSES или GROSS
     * @param order  направление порядка: asc или desc; не задан — как привычнее для
     *               выбранного критерия
     * @param onlyWithSales оставить только товары, у которых были продажи
     * @param page   номер страницы с нуля
     * @param size   размер страницы, максимум 500
     */
    @GetMapping("/products")
    public ProductReport productReport(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order,
            @RequestParam(defaultValue = "false") boolean onlyWithSales,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        SellerAccount account = accountLookup.requireAccount(accountId);
        return productAnalytics.productReport(account.getMarketplace().getCode(),
                Optional.of(account.getId()), dateFrom, dateTo, author, query,
                sort, order, onlyWithSales, page, size);
    }

    /**
     * GET /api/accounts/1/analytics/products/dynamics?dateFrom=…&dateTo=…
     *
     * <p>Продажи и возвраты по дням периода — для графика под таблицей товаров.
     * Количество в штуках, а не суммы: у отчёта по товарам строки это товары, и
     * без этого запроса пришлось бы скачивать весь отчёт только ради рисунка.
     *
     * <p>Без фильтров показывает все товары. {@code author} и {@code sku} —
     * два разных отбора, и одновременно они не применяются: при заданном
     * {@code sku} автор игнорируется, иначе пришлось бы искать товар сразу у двух
     * продавцов. Задача у пользователя одна.
     *
     * <p>Дни без операций в {@code days} не попадают, а состояние периода приходит
     * в {@code coverage}: без него «день без продаж» и «день не загружен» на графике
     * выглядели бы одинаково.
     *
     * @param author автор ровно в том написании, в каком указан в карточке товара
     * @param sku    конкретный SKU
     */
    @GetMapping("/products/dynamics")
    public SalesDynamicsReport salesDynamics(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) Long sku) {

        SellerAccount account = accountLookup.requireAccount(accountId);
        return productAnalytics.salesDynamics(account.getMarketplace().getCode(),
                Optional.of(account.getId()), dateFrom, dateTo, author, sku);
    }

    }