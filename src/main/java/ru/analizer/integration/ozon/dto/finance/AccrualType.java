package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * finance.v1.GetFinanceAccrualTypesResponse.AccrualType
 * Пример: 1 → Acquiring, 74 → StarsMembership. Список открыт и может пополняться.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccrualType(
        @JsonProperty("id") Integer id,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description
) {
}
