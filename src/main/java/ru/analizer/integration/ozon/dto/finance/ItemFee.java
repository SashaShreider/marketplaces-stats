package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.analizer.integration.ozon.json.LenientLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.List;

/**
 * Начисления по одному SKU внутри одной ITEM-операции.
 *
 * <p>{@code quantity} также отсутствует в опубликованной схеме, но приходит в реальном ответе.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ItemFee(
        @JsonProperty("sku") @JsonDeserialize(using = LenientLongDeserializer.class) Long sku,
        @JsonProperty("quantity") @JsonDeserialize(using = LenientLongDeserializer.class) Long quantity,
        @JsonProperty("fees") List<ItemFeeDetail> fees
) {
    public List<ItemFeeDetail> safeFees() {
        return fees == null ? List.of() : fees;
    }

    public int quantityOrDefault() {
        return quantity == null || quantity <= 0 ? 1 : quantity.intValue();
    }
}
