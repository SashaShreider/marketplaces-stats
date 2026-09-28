package ru.analizer.marketplace.ozon.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigDecimal;

/**
 * OZON отдаёт суммы строками ({@code "amount": "-26.43"}), но может вернуть и число,
 * а также {@code null} или пустую строку. Приводим всё к {@link BigDecimal}.
 */
public class MoneyDeserializer extends ValueDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        return toBigDecimal(context.readTree(parser));
    }

    public static BigDecimal toBigDecimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isContainer()) {
            return null;
        }
        String raw = node.asString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return new BigDecimal(raw.trim());
    }
}
