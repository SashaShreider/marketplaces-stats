package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * finance.v1.GetFinanceAccrualByDayResponse.Accrual.Posting
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Posting(
        @JsonProperty("delivery_schema") String deliverySchema,
        @JsonProperty("delivery_speed") Integer deliverySpeed,
        @JsonProperty("products") List<PostingProduct> products
) {
    public List<PostingProduct> safeProducts() {
        return products == null ? List.of() : products;
    }
}
