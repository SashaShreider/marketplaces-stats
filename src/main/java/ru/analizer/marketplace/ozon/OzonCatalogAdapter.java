package ru.analizer.marketplace.ozon;

import org.springframework.stereotype.Component;
import ru.analizer.marketplace.CatalogPage;
import ru.analizer.marketplace.ProductCatalogAdapter;
import ru.analizer.marketplace.ozon.dto.ProductAttributesFilter;
import ru.analizer.marketplace.ozon.dto.ProductAttributesRequest;

import java.util.List;

/**
 * Источник характеристик товаров OZON.
 *
 * <p>Знает только одно: каким фильром берутся ВСЕ товары аккаунта.
 * Всё остальное — заголовки, повторы, разбор ошибок, разбор ответа — в
 * {@link OzonClient}, как и у финансовых методов.
 */
@Component
public class OzonCatalogAdapter implements ProductCatalogAdapter {

    /**
     * Показываем все товары, включая скрытые: отчёт по товарам должен быть полным,
     * иначе продавец не увидит то, что перестало продаваться.
     *
     * <p>Значение — строка, а не массив: {@code ["ALL"]} OZON отклоняет с ошибкой
     * {@code invalid value for enum field visibility}.
     */
    private static final String VISIBILITY_ALL = "ALL";

    /** Максимум по спецификации — 1000 за один ответ. */
    private static final int DEFAULT_LIMIT = 1000;

    private final OzonClient client;

    public OzonCatalogAdapter(OzonClient client) {
        this.client = client;
    }

    @Override
    public String marketplaceCode() {
        return OzonAdapter.MARKETPLACE_CODE;
    }

    @Override
    public CatalogPage fetchProducts(String lastId, int limit) {
        ProductAttributesFilter filter = new ProductAttributesFilter(
                VISIBILITY_ALL, null, null, null);
        return client.getProductAttributes(
                new ProductAttributesRequest(filter, lastId == null ? "" : lastId, limit, null));
    }

    @Override
    public CatalogPage fetchProductsBySku(List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return new CatalogPage(List.of(), 0, "");
        }
        return client.getProductAttributes(new ProductAttributesRequest(
                ProductAttributesFilter.bySku(skus), "", DEFAULT_LIMIT, null));
    }
}