package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Начисление по продавцу без привязки к товару. Такие расходы не распределяются между SKU.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NonItemFee(
        @JsonProperty("type_id") Integer typeId,
        @JsonProperty("accrued") Money accrued
) {
}
