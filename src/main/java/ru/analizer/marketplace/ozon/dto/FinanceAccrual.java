package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;
import ru.analizer.marketplace.ozon.json.LenientLongDeserializer;

import java.time.LocalDate;
import java.util.List;

/**
 * finance.v1.GetFinanceAccrualByDayResponse.Accrual — одна финансовая операция.
 * Одна операция может относиться к одному отправлению (unit_number), а одно отправление —
 * к нескольким операциям. Обратное тоже верно: в одной операции несколько товаров.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceAccrual(
        @JsonProperty("accrual_id") Long accrualId,
        @JsonProperty("date") String date,
        @JsonProperty("total_amount") Money totalAmount,
        @JsonProperty("unit_number") String unitNumber,
        @JsonProperty("accrued_category") String accruedCategory,
        @JsonProperty("type_id") Integer typeId,
        @JsonProperty("posting") Posting posting,
        @JsonProperty("item_fees") ItemFees itemFees,
        @JsonProperty("non_item_fee") NonItemFee nonItemFee,
        @JsonProperty("container_fees") ContainerFees containerFees
) {
    public AccruedCategory category() {
        if (accruedCategory == null || accruedCategory.isBlank()) {
            return AccruedCategory.UNSPECIFIED;
        }
        try {
            return AccruedCategory.valueOf(accruedCategory);
        } catch (IllegalArgumentException e) {
            return AccruedCategory.UNKNOWN;
        }
    }

    public LocalDate accruedDate() {
        return date == null || date.isBlank() ? null : LocalDate.parse(date);
    }
}
