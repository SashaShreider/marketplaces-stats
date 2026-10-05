package ru.analizer.integration.ozon.dto.finance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;
import ru.analizer.integration.ozon.json.CommissionRatioDeserializer;

/**
 * Итоговая комиссия с учётом скидок и наценки.
 * Значения отрицательные — это расход, а не приход.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Commission(
        @JsonProperty("seller_price") Money sellerPrice,
        @JsonProperty("sale_price") Money salePrice,
        @JsonProperty("sale_commission") Money saleCommission,
        @JsonProperty("commission") Money commission,
        @JsonProperty("commission_ratio") @JsonDeserialize(using = CommissionRatioDeserializer.class) String commissionRatio,
        @JsonProperty("sale_amount") Money saleAmount,
        @JsonProperty("coinvestment") Money coinvestment,
        @JsonProperty("bonus") Money bonus
) {
}
