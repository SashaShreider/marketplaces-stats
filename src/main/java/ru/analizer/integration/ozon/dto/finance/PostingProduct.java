package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import ru.analizer.integration.ozon.json.LenientLongDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/**
 * finance.v1.GetFinanceAccrualByDayResponse.Accrual.Posting.Product
 *
 * <p>Поля {@code quantity} нет в опубликованной схеме OZON, но реально приходит в ответе
 * (проверено на реальном ответе за 2026-04-10). Поле {@code commission} может быть {@code null} —
 * бывают начисления только по логистике.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostingProduct(
        @JsonProperty("sku") @JsonDeserialize(using = LenientLongDeserializer.class) Long sku,
        @JsonProperty("quantity") @JsonDeserialize(using = LenientLongDeserializer.class) Long quantity,
        @JsonProperty("commission") Commission commission,
        @JsonProperty("delivery") Delivery delivery
) {
    /**
     * Количество единиц товара. Если OZON поле не прислала, считаем строки единицами товара.
     */
    public int quantityOrDefault() {
        return quantity == null || quantity <= 0 ? 1 : quantity.intValue();
    }
}
