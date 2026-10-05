package ru.analizer.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.analytics.DailyAnalyticsService;
import ru.analizer.analytics.DailyReport;
import ru.analizer.analytics.ProductAnalyticsService;
import ru.analizer.analytics.ProductReport;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Отчёты по сохранённым данным.
 *
 * <p>Ни один метод не обращается к API маркетплейса: отчёты считаются по нашей базе.
 * Поэтому их можно вызывать часто и без риска исчерпать квоту.
 *
 * <p>Каждый отчёт всегда сообщает, можно ли ему доверять, через поля {@code status} и
 * {@code coverage}. Нули за незагруженный день неотличимы от нулей за день без
 * начислений, и без этих полей пользователь решил бы, что денег не было.
 */
@RestController
@RequestMapping("/api/marketplaces/{marketplace}/analytics")
public class AnalyticsController {

    private final DailyAnalyticsService dailyAnalytics;
    private final ProductAnalyticsService productAnalytics;

    public AnalyticsController(DailyAnalyticsService dailyAnalytics,
                               ProductAnalyticsService productAnalytics) {
        this.dailyAnalytics = dailyAnalytics;
        this.productAnalytics = productAnalytics;
    }

    /**
     * GET /api/marketplaces/ozon/analytics/daily?dateFrom=…&dateTo=…
     *
     * <p>Разбивка доходов, расходов и выплаты по дням периода, с детализацией расходов
     * по типам начислений.
     *
     * @param dateFrom начало периода включительно
     * @param dateTo   конец периода включительно
     */
    @GetMapping("/daily")
    public DailyReport dailyReport(
            @PathVariable String marketplace,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        return dailyAnalytics.dailyReport(marketplaceCode(marketplace), dateFrom, dateTo);
    }

    /**
     * GET /api/marketplaces/ozon/analytics/products?dateFrom=…&dateTo=…
     *
     * <p>Все товары каталога вместе с их продажами и списаниями за период.
     *
     * <p>Строки — все товары, а не только проданные. Ноль у товара, который есть в
     * каталоге, — честный ответ «продаж не было»; если же период загружен не полностью,
     * это указано в {@code status} и {@code coverage}, и нулям верить нельзя.
     *
     * <p>Фильтр {@code author} сравнивается со сведёнными именами «фамилия инициалы»,
     * поэтому «Сурцуков», «Сурцуков А.» и «Сурцуков Анатолий» находят одни и те же
     * товары. Подсказки для этого поля — {@code GET /data/authors}.
     *
     * @param author автор в любом написании, которое ввёл продавец
     * @param query  подстрока названия, своего артикула или ISBN
     * @param sort   INCOME (по умолчанию), NAME или SKU
     * @param page   номер страницы с нуля
     * @param size   размер страницы, максимум 500
     */
    @GetMapping("/products")
    public ProductReport productReport(
            @PathVariable String marketplace,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        return productAnalytics.productReport(
                marketplaceCode(marketplace), dateFrom, dateTo, author, query, sort, page, size);
    }

    /** Код маркетплейса в верхнем регистре — так он хранится и возвращается в ответе. */
    private String marketplaceCode(String marketplace) {
        return marketplace.trim().toUpperCase(Locale.ROOT);
    }
}