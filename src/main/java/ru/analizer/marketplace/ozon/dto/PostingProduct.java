package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;
import ru.analizer.marketplace.ozon.json.LenientLongDeserializer;

/**
 * finance.v1.GetFinanceAccrualByDayResponse.Accrual.Posting.Product
 * Внимание: поля quantity в этом методе нет — OZON не отдаёт количество единиц товара.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostingProduct(
        @JsonProperty("sku") @JsonDeserialize(using = LenientLongDeserializer.class) Long sku,
        @JsonProperty("commission") Commission commission,
        @JsonProperty("delivery") Delivery delivery
) {
}
