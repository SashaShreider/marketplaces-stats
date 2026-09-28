package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;
import ru.analizer.marketplace.ozon.json.LenientLongDeserializer;

import java.util.List;

/**
 * Начисления по одному SKU внутри одной ITEM-операции.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemFee(
        @JsonProperty("sku") @JsonDeserialize(using = LenientLongDeserializer.class) Long sku,
        @JsonProperty("fees") List<ItemFeeDetail> fees
) {
    public List<ItemFeeDetail> safeFees() {
        return fees == null ? List.of() : fees;
    }
}
