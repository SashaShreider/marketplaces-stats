package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Услуга доставки. {@code type_id} не зашиваем в бизнес-логику — справочник приходит из
 * {@code /v1/finance/accrual/types}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeliveryService(
        @JsonProperty("type_id") Integer typeId,
        @JsonProperty("accrued") Money accrued
) {
}
