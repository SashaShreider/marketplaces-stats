package ru.analizer.integration.ozon.dto.finance;

import ru.analizer.integration.ozon.json.MoneyDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

/**
 * {@code money.Money*} из схемы OZON. Сумма приходит строкой и может быть отрицательной.
 * Десериализатор вешается на компонент {@code amount}, а не на весь record: иначе Jackson
 * попытался бы разобрать объект {@code {"amount": ..., "currency": ...}} в BigDecimal.
 */
public record Money(
        @JsonDeserialize(using = MoneyDeserializer.class) BigDecimal amount,
        String currency
) {
}
