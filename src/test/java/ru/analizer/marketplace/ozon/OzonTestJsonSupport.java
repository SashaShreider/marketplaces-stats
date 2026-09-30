package ru.analizer.marketplace.ozon;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * Сравнение JSON по значению, а не по тексту: пробелы и порядок ключей несущественны.
 */
final class OzonTestJsonSupport {

    private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private OzonTestJsonSupport() {
    }

    static boolean sameJson(String first, String second) {
        try {
            return Objects.equals(MAPPER.readTree(first), MAPPER.readTree(second));
        } catch (Exception e) {
            return false;
        }
    }
}
