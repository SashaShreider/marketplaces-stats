package ru.analizer.integration.ozon.dto.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Товар из ответа {@code /v4/product/info/attributes}.
 *
 * <p>Поля, которые выгрузка действительно возвращает, проверены на реальном ответе:
 * {@code id} — внутренний идентификатор (не равен {@code sku}), {@code sku} —
 * идентификатор товара, связывающий каталог с начислениями.
 *
 * <p>Пустые списки и {@code null} приходят штатно: {@code images} в выгрузке пуст,
 * {@code complex_attributes} — пустой массив. Для них есть {@code safeXxx()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductInfoV4(
        @JsonProperty("id") Long id,
        @JsonProperty("sku") Long sku,
        @JsonProperty("offer_id") String offerId,
        @JsonProperty("name") String name,
        @JsonProperty("barcode") String barcode,
        @JsonProperty("type_id") Long typeId,
        @JsonProperty("description_category_id") Long descriptionCategoryId,
        @JsonProperty("primary_image") String primaryImage,
        @JsonProperty("images") List<String> images,
        @JsonProperty("height") Integer height,
        @JsonProperty("width") Integer width,
        @JsonProperty("depth") Integer depth,
        @JsonProperty("dimension_unit") String dimensionUnit,
        @JsonProperty("weight") Integer weight,
        @JsonProperty("weight_unit") String weightUnit,
        @JsonProperty("model_info") ModelInfo modelInfo,
        @JsonProperty("attributes") List<ProductAttributeV4> attributes,
        @JsonProperty("complex_attributes") List<JsonNode> complexAttributes
) {

    public List<ProductAttributeV4> safeAttributes() {
        return attributes == null ? List.of() : attributes;
    }

    public List<JsonNode> safeComplexAttributes() {
        return complexAttributes == null ? List.of() : complexAttributes;
    }

    public Long modelId() {
        return modelInfo == null ? null : modelInfo.modelId();
    }

    /** {@code model_info} в ответе приходит как объект с идентификатором и количеством. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ModelInfo(
            @JsonProperty("model_id") Long modelId,
            @JsonProperty("count") Integer count
    ) {
    }
}