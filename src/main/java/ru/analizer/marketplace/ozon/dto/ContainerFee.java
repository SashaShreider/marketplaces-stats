package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Начисление по контейнеру: тип + сумма.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContainerFee(
        @JsonProperty("type_id") Integer typeId,
        @JsonProperty("accrued") Money accrued
) {
}
