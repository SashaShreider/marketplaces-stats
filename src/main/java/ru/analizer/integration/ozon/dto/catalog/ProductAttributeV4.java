package ru.analizer.integration.ozon.dto.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Характеристика товара: {@code {id, values: [{value, dictionary_value_id}]}}.
 *
 * <p>Значение — массив, но в выгрузке у каждого атрибута оно ровно одно. Порядок
 * сохраняем на случай, если значений станет несколько: для наборов вроде цветов он
 * значим.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductAttributeV4(
        @JsonProperty("id") Long id,
        @JsonProperty("values") List<ProductAttributeValueV4> values,
        @JsonProperty("complex_id") Long complexId
) {

    public List<ProductAttributeValueV4> safeValues() {
        return values == null ? List.of() : values;
    }
}