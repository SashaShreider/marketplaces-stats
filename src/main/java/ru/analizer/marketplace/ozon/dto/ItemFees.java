package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * finance.v1.GetFinanceAccrualByDayResponse.Accrual.ItemFees — расходы, привязанные к SKU.
 * Один ITEM может содержать несколько SKU и несколько типов расходов.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemFees(
        @JsonProperty("fees") List<ItemFee> fees
) {
    public List<ItemFee> safeFees() {
        return fees == null ? List.of() : fees;
    }
}
