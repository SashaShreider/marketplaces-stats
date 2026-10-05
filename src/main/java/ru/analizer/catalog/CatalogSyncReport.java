package ru.analizer.catalog;

/**
 * Итог загрузки каталога товаров.
 *
 * <p>{@code failedProducts} отдельно от {@code savedProducts}, потому что молчаливый
 * частичный успех хуже явной ошибки: если из 108 товаров записались 106, пользователь
 * должен об этом знать, иначе в отчёте просто исчезнут два товара без объяснения.
 */
public record CatalogSyncReport(
        int totalProducts,
        int savedProducts,
        int failedProducts,
        boolean complete
) {

    public static CatalogSyncReport of(int total, int saved, int failed) {
        return new CatalogSyncReport(total, saved, failed, failed == 0);
    }
}