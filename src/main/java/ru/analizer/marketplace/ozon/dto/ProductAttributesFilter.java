package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Фильтр {@code productv4.Filter}.
 *
 * <p>Все поля опциональны, но сам {@code filter} — нет: без него OZON отвечает ошибкой
 * валидации. {@code sku} — массив строк, хотя в ответе {@code sku} приходит числом;
 * это расхождение есть в самой спецификации OZON.
 *
 * @param visibility показывает все товары, включая скрытые: {@code ALL}, {@code VISIBLE},
 *                   {@code INVISIBLE} — это строка, а не массив
 * @param offerId    свои артикулы продавца
 * @param productId  внутренние идентификаторы OZON
 * @param sku        до 1000 SKU за запрос — этим пользуемся для точечного обновления
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductAttributesFilter(
        @JsonProperty("visibility") String visibility,
        @JsonProperty("offer_id") List<String> offerId,
        @JsonProperty("product_id") List<String> productId,
        @JsonProperty("sku") List<String> sku
) {

    /** Фильтр по списку SKU: догружаем только нужные товары. */
    public static ProductAttributesFilter bySku(List<String> skuList) {
        return new ProductAttributesFilter(null, null, null, skuList);
    }
}