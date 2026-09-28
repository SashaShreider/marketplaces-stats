package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * finance.v1.GetFinanceAccrualByDayResponse
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceAccrualByDayResponse(
        @JsonProperty("accruals") List<FinanceAccrual> accruals,
        @JsonProperty("last_id") String lastId
) {
    public boolean hasNextPage() {
        return lastId != null && !lastId.isBlank();
    }

    public List<FinanceAccrual> safeAccruals() {
        return accruals == null ? List.of() : accruals;
    }
}
