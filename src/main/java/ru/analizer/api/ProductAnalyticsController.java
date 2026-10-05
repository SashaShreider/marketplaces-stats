package ru.analizer.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.analytics.ProductAnalyticsService;
import ru.analizer.analytics.ProductReport;
import ru.analizer.marketplace.ozon.OzonProperties;

import java.time.LocalDate;

/**
 * Отчёт по товарам.
 *
 * <p>Строки отчёта — все товары каталога, а не только проданные. Ноль у товара, который
 * есть в каталоге, означает «продаж не было»; если же период загружен не полностью,
 * это указано в {@code status} и {@code coverage}, и нулям верить нельзя.
 */
@RestController
@RequestMapping("/api/analytics")
public class ProductAnalyticsController {

    private final ProductAnalyticsService products;
    private final OzonProperties ozonProperties;

    public ProductAnalyticsController(ProductAnalyticsService products, OzonProperties ozonProperties) {
        this.products = products;
        this.ozonProperties = ozonProperties;
    }

    /**
     * GET /api/analytics/products
     *
     * @param author имя автора; сравнивается со сведёнными ключами, поэтому «Сурцуков»
     *               находит и «Сурцуков А.», и «Сурцуков Анатолий»
     * @param query  подстрока названия, артикула или ISBN
     * @param sort   INCOME (по умолчанию), NAME или SKU
     */
    @GetMapping("/products")
    public ProductReport products(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String author,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String clientId) {

        String account = clientId == null || clientId.isBlank() ? ozonProperties.clientId() : clientId;
        return products.products(account, "OZON", dateFrom, dateTo, author, query, sort, page, size);
    }
}