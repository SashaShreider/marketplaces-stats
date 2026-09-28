package ru.analizer.marketplace.ozon.dto;

/**
 * finance.v1.GetFinanceAccrualByDayResponse.Accrual.AccruedCategory.Enum
 * OZON может добавлять новые категории, поэтому {@link #UNKNOWN} — безопасный fallback.
 */
public enum AccruedCategory {
    UNSPECIFIED,
    POSTING,
    ITEM,
    NON_ITEM,
    CONTAINER_FEES,
    UNKNOWN
}
