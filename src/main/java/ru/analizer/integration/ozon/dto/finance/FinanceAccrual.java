package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * finance.v1.GetFinanceAccrualByDayResponse.Accrual — одна финансовая операция.
 * Одна операция может относиться к одному отправлению (unit_number), а одно отправление —
 * к нескольким операциям. Обратное тоже верно: в одной операции несколько товаров.
 *
 * <p>{@code typeId} объявлен в схеме OZON, но в реальном ответе за 2026-04-10 не приходил ни
 * разу — тип начисления лежит на уровне детализации ({@code non_item_fee.type_id},
 * {@code item_fees.fees[].type_id}, {@code delivery.services[].type_id}). Поле оставлено
 * на случай появления его в будущем.
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
