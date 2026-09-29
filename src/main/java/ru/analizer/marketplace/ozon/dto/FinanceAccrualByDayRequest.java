package ru.analizer.marketplace.ozon.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST /v1/finance/accrual/by-day — тело запроса.
 * Пагинации кроме {@code last_id} нет: ни limit, ни offset API не принимает.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinanceAccrualByDayRequest(
        @JsonProperty("date") String date,
        @JsonProperty("last_id") String lastId
) {
    public static FinanceAccrualByDayRequest firstPage(String date) {
        return new FinanceAccrualByDayRequest(date, "");
    }

    public static FinanceAccrualByDayRequest nextPage(String date, String lastId) {
        return new FinanceAccrualByDayRequest(date, lastId);
    }
}
