package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import ru.analizer.sync.infrastructure.entity.Posting;

/**
 * Строка товара внутри операции POSTING, выгруженная из базы для аналитики.
 *
 * <p>Значения хранятся со знаком, как их отдаёт OZON: расходы отрицательные.
 * Сведение знаков — задача {@link FinancialModel}, а не SQL.
 */
public record ProductFact(
        java.time.LocalDate date,
        String unitNumber,
        Long accrualId,
        Long sku,
        int quantity,
        BigDecimal salePrice,
        BigDecimal saleCommission,
        BigDecimal bonus,
        BigDecimal coinvestment
) {
}
