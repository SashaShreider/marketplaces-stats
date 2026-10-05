package ru.analizer.catalog;

/**
 * Получатель прогресса загрузки каталога.
 *
 * <p>Отдельный интерфейс от {@link ru.analizer.sync.SyncProgressListener} намеренно: у
 * каталога нет дат, поэтому «12 из 30 дней» здесь было бы бессмысленным — только число
 * товаров и текущий SKU.
 */
public interface CatalogProgressListener {

    /** Всего товаров по данным OZON — известно из ответа до записи. */
    void onStart(int totalProducts);

    /** Обработано товаров, включая неудачные. */
    void onProduct(int processed, int total, long sku);

    void onProductFailed(long sku, RuntimeException error);
}