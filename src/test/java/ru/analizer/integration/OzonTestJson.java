package ru.analizer.integration;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.Objects;

/**
 * Сравнение JSON по значению, а не по тексту.
 *
 * <p>Нужно потому, что PostgreSQL {@code jsonb} нормализует пробелы и порядок ключей:
 * байт-в-байт исходная строка не сохраняется, но все значения сохраняются.
 */
final class OzonTestJson {

    private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private OzonTestJson() {
    }

    static boolean sameJson(String first, String second) {
        try {
            return Objects.equals(MAPPER.readTree(first), MAPPER.readTree(second));
        } catch (Exception e) {
            return false;
        }
    }
}
