package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * finance.v1.GetFinanceAccrualTypesResponse
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceAccrualTypesResponse(
        @JsonProperty("accrual_types") List<AccrualType> accrualTypes
) {
    public List<AccrualType> safeAccrualTypes() {
        return accrualTypes == null ? List.of() : accrualTypes;
    }
}
