package ru.analizer.integration;

import ru.analizer.account.domain.MarketplaceCredentials;
import ru.analizer.integration.model.CatalogPage;
import ru.analizer.integration.model.ProductEntry;

import java.util.List;

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
     * Карточки товаров, снятых с продажи, помеченные как архивные.
     *
     * <p>Нужны потому, что основной обход архивных не видит: у OZON {@code ALL} —
     * это «все товары, кроме архивных». Без догрузки отчёт по товарам расходился бы с
     * дневным на сумму продаж снятого товара, и объяснить расхождение было бы нечем.
     *
     * <p>По умолчанию пусто: маркетплейс, у которого таких товаров не бывает, ничего
     * не должен для этого реализовывать.
     *
     * @param skus SKU из начислений, не найденные при обходе каталога
     * @return карточки с признаком архивности; пусто, если таких товаров нет
     */
    default List<ProductEntry> fetchArchivedBySku(MarketplaceCredentials credentials, List<String> skus) {
        return List.of();
    }

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