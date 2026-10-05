package ru.analizer.integration.ozon.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * {@code commission_ratio} приходит строкой вида {@code "value:\"0\""} или числом.
 * Сохраняем значение как есть — это справочная доля, а не деньги.
 */
public class CommissionRatioDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        return parse(context.readTree(parser));
    }

    public static String parse(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isContainer()) {
            return null;
        }
        if (node.isNumber()) {
            return node.decimalValue().toPlainString();
        }
        String raw = node.asString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        int colon = trimmed.indexOf(':');
        if (colon < 0) {
            return trimmed;
        }
        String tail = trimmed.substring(colon + 1).trim();
        if (tail.length() >= 2 && tail.startsWith("\"") && tail.endsWith("\"")) {
            tail = tail.substring(1, tail.length() - 1);
        }
        return tail.isBlank() ? null : tail;
    }
}
