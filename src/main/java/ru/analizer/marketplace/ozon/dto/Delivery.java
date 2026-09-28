package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Начисления по доставке для одного товара. Сумма логистики = сумма services[].accrued.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Delivery(
        @JsonProperty("total_accrued") Money totalAccrued,
        @JsonProperty("services") List<DeliveryService> services
) {
    public List<DeliveryService> safeServices() {
        return services == null ? List.of() : services;
    }
}
