package ru.analizer.analytics.infrastructure.filter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Фильтр по конкретным товарам — отбор «эти три книги».
 *
 * <p>Тот же отбор, что и в дневном отчёте: выбрав товары, продавец спрашивает про
 * них, а не про весь магазин. Без него страница товаров умела бы только «все» или
 * «один автор», хотя на «Обзоре» выбрать несколько книг — обычное дело.
 *
 * <p>Значения передаются строкой через запятую, а не массивом: PostgreSQL-массив
 * пришлось бы собирать через {@code createArrayOf}, а именованные параметры
 * {@code NamedParameterJdbcTemplate} этого не умеют и падают на «не могу определить
 * тип для ArrayList». Список номеров товаров короткий, и разбор строки на стороне
 * базы стоит дешевле, чем второй способ передавать параметры.
 */
public final class ProductSkuFilter implements ProductAttributeFilter {

    private static final String VALUE = "skuList";

    private final String value;

    private ProductSkuFilter(String value) {
        this.value = value;
    }

    /**
     * Фильтр по списку SKU.
     *
     * @param skus номера товаров; пустой список или {@code null} — без фильтра
     */
    public static ProductSkuFilter bySkus(List<Long> skus) {
        if (skus == null || skus.isEmpty()) {
            return new ProductSkuFilter("");
        }
        String joined = skus.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::valueOf)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return new ProductSkuFilter(joined);
    }

    @Override
    public String predicate(String alias) {
        if (value.isEmpty()) {
            return null;
        }
        // Номера приходят числами из Long, поэтому подстановка через список строк
        // безопасна: в значение не может попасть ничего, кроме цифр и запятых.
        return alias + ".sku in (select unnest(string_to_array(:" + VALUE + ", ',')::bigint[]))";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(VALUE, value);
        return parameters;
    }
}
