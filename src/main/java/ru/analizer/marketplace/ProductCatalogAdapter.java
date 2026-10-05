package ru.analizer.marketplace;

import java.util.List;
import ru.analizer.account.domain.MarketplaceCredentials;

/**
 * Источник характеристик товаров.
 *
 * <p>Отделён от {@link MarketplaceAdapter} намеренно: там только финансы, и добавление
 * методов каталога заставило бы переписывать все тестовые заглушки адаптера.
 */
public interface ProductCatalogAdapter {

    String marketplaceCode();

    /**
     * Одна страница характеристик товаров.
     *
     * <p>Реквизиты передаются явно: у каждого пользователя свои ключи, поэтому
     * общий бин адаптера не может их содержать.
     */
    CatalogPage fetchProducts(MarketplaceCredentials credentials, String lastId, int limit);

    /** Характеристики конкретных товаров — до 1000 SKU за запрос. */
    CatalogPage fetchProductsBySku(MarketplaceCredentials credentials, List<String> skus);

    /**
     * Обход всех страниц.
     *
     * <p>Остановка здесь намеренно не по пустому курсору: у этого метода OZON курсор
     * непустой даже на последней странице. Цикл останавливается на трёх условиях
     * сразу: страница пуста, курсор не сдвинулся, либо собрано {@code total} товаров.
     */
    default List<ProductEntry> fetchAllProducts(MarketplaceCredentials credentials, int limit) {
        return CatalogPager.fetchAll(this, credentials, limit);
    }
}