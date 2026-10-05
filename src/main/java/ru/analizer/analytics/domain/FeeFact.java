package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Строка расхода, у которой OZON указал тип начисления: услуга доставки,
 * начисление по товару (ITEM) или расход без привязки к SKU (NON_ITEM/CONTAINER).
 */
public record FeeFact(
        LocalDate date,
        String unitNumber,
        Long accrualId,
        Long sku,
        Integer typeId,
        BigDecimal amount,
        FeeKind kind
) {

    /**
     * Откуда пришло начисление. Важно для отчёта по товарам: расходы без SKU
     * нельзя искусственно распределять между товарами.
     */
    public enum FeeKind {
        /** Логистика: posting_product -&gt; delivery_service. */
        DELIVERY,
        /** ITEM: расход, привязанный к SKU. */
        ITEM,
        /** NON_ITEM: расход без привязки к SKU. */
        NON_ITEM,
        /** CONTAINER_FEES: начисление по контейнеру. */
        CONTAINER
    }
}
