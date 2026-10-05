package ru.analizer.integration.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Маркетплейс-независимое представление одной финансовой операции.
 *
 * <p>Смысловые категории (продажа, возврат, комиссия, логистика, прочие расходы) не выделяются
 * здесь намеренно: это аналитическая интерпретация, которая по SPEC.md относится к этапу 2.
 * Адаптер отдаёт ровно то, что отдал API.
 */
public record AccrualDto(
        Long externalId,
        LocalDate date,
        String unitNumber,
        Category category,
        Integer typeId,
        BigDecimal totalAmount,
        String currency,
        Posting posting,
        List<ItemFee> itemFees,
        FeeDetail nonItemFee,
        List<FeeDetail> containerFees,
        String rawJson
) {

    public enum Category {
        POSTING,
        ITEM,
        NON_ITEM,
        CONTAINER_FEES,
        UNSPECIFIED,
        UNKNOWN
    }

    public record Posting(String deliverySchema, Integer deliverySpeed, List<Product> products) {
    }

    public record Product(
            Long sku,
            int quantity,
            Commission commission,
            BigDecimal deliveryTotalAccrued,
            String deliveryCurrency,
            List<FeeDetail> deliveryServices
    ) {
    }

    public record Commission(
            BigDecimal sellerPrice,
            BigDecimal salePrice,
            BigDecimal saleAmount,
            BigDecimal saleCommission,
            BigDecimal commission,
            String commissionRatio,
            BigDecimal coinvestment,
            BigDecimal bonus,
            String currency
    ) {
    }

    public record ItemFee(Long sku, int quantity, List<FeeDetail> fees) {
    }

    public record FeeDetail(Integer typeId, BigDecimal amount, String currency) {
    }
}
