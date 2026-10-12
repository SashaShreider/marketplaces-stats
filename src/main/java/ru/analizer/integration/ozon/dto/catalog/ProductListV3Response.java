package ru.analizer.integration.ozon.dto.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Ответ {@code POST /v3/product/list} (productv3.GetProductListV3Response).
 *
 * <p>Метод отдаёт куда меньше, чем {@code /v4/product/info/attributes}: только
 * идентификаторы и состояние, без характеристик, авторов и обложек. Поэтому он
 * используется не как источник карточки, а как ответ на вопрос «этот SKU в
 * архиве или нет» — после чего характеристики берутся привычным способом.
 *
 * @param items  товары страницы
 * @param total  сколько товаров у продавца всего
 * @param lastId курсор следующей страницы
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductListV3Response(
        @JsonProperty("items") ProductListV3Item[] items,
        @JsonProperty("total") Integer total,
        @JsonProperty("last_id") String lastId
) {

    /**
     * Товар в ответе {@code /v3/product/list}.
     *
     * <p>{@code sku} приходит числом, хотя в фильтре запрашивается массивом строк —
     * расхождение есть в самой спецификации OZON, поэтому тип приведён к числу.
     *
     * @param sku      идентификатор товара
     * @param offerId  артикул продавца; может оказаться единственным, что известно о товаре
     * @param archived снят ли товар с продажи
     * @param visible  виден ли товар покупателям
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductListV3Item(
            @JsonProperty("sku") Long sku,
            @JsonProperty("offer_id") String offerId,
            @JsonProperty("archived") Boolean archived,
            @JsonProperty("visible") Boolean visible
    ) {

        public boolean isArchived() {
            return Boolean.TRUE.equals(archived);
        }
    }
}
