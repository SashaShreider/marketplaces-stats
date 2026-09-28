package ru.analizer.marketplace.ozon.dto;

import tools.jackson.databind.annotation.JsonDeserialize;
import ru.analizer.marketplace.ozon.json.MoneyDeserializer;

import java.math.BigDecimal;

/**
 * {@code money.Money*} из схемы OZON. Сумма приходит строкой, может быть отрицательной.
 */
@JsonDeserialize(using = MoneyDeserializer.class)
public record Money(BigDecimal amount, String currency) {
}
