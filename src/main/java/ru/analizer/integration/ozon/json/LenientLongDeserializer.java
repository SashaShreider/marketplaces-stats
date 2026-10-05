package ru.analizer.integration.ozon.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

/**
 * SKU в схеме объявлен как int64, но встречается и как число, и как строка.
 */
public class LenientLongDeserializer extends ValueDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        return parse(context.readTree(parser));
    }

    public static Long parse(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || node.isContainer()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        String raw = node.asString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Long.parseLong(raw.trim());
    }
}
