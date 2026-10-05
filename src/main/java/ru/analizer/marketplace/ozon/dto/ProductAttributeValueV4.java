package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Значение характеристики товара.
 *
 * <p>В выгрузке {@code dictionary_value_id} всегда 0 — это поле для значений из
 * справочников OZON, и оно пригодится, когда атрибут окажется ссылочным.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductAttributeValueV4(
        @JsonProperty("value") String value,
        @JsonProperty("dictionary_value_id") Long dictionaryValueId
) {
}