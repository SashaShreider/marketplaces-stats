package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Детализация начисления по товару: тип расхода + сумма.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemFeeDetail(
        @JsonProperty("type_id") Integer typeId,
        @JsonProperty("accrued") Money accrued
) {
}
