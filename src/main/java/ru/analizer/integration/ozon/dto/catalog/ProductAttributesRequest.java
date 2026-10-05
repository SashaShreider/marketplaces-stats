package ru.analizer.integration.ozon.dto.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


/**
 * Тело запроса {@code POST /v4/product/info/attributes}
 * (productv4.GetProductAttributesV4Request).
 *
 * <p>Два наблюдения, полученные реальными запросами, а не из спецификации:
 * <ul>
 *   <li>{@code filter} обязателен. В спецификации {@code required: true} стоит только
 *       на {@code limit}, но без {@code filter} OZON отвечает
 *       {@code code: 3 — invalid GetProductAttributesV4Request.Filter: value is required}.
 *       Чтобы получить все товары, в фильтр кладём {@code visibility = "ALL"}.</li>
 *   <li>{@code visibility} — строковое перечисление, а не массив. Значение
 *       {@code ["ALL"]} отклоняется: {@code invalid value for enum field visibility}.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductAttributesRequest(
        @JsonProperty("filter") ProductAttributesFilter filter,
        @JsonProperty("last_id") String lastId,
        @JsonProperty("limit") Integer limit,
        @JsonProperty("sort_by") String sortBy
) {
}