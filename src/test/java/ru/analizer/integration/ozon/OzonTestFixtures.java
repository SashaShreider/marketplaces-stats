package ru.analizer.integration.ozon;

import ru.analizer.integration.model.AccrualDto;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Общая загрузка фикстур для тестов разбора ответов OZON.
 */
final class OzonTestFixtures {

    private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private OzonTestFixtures() {
    }

    static tools.jackson.databind.ObjectMapper mapper() {
        return MAPPER;
    }

    /**
     * @param resource путь к фикстуре в {@code src/test/resources}, например
     *                 {@code fixtures/accruals-2026-04-10.json}
     */
    static List<AccrualDto> load(String resource) throws IOException {
        String raw = read(resource);
        var root = MAPPER.readTree(raw);
        List<AccrualDto> result = new ArrayList<>();
        for (var node : root.get("accruals")) {
            result.add(OzonMapper.toAccrualDto(
                    MAPPER.treeToValue(node, ru.analizer.integration.ozon.dto.finance.FinanceAccrual.class),
                    node.toString()));
        }
        return result;
    }

    static AccrualDto byExternalId(String resource, Long externalId) throws IOException {
        return load(resource).stream()
                .filter(d -> externalId.equals(d.externalId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("accrual " + externalId + " не найден в " + resource));
    }

    static String read(String resource) throws IOException {
        try (InputStream in = open(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Читает фикстуру из classpath.
     *
     * <p>Раньше здесь был запасной вариант «прочитать из файловой системы», из-за чего
     * фикстура лежала в корне проекта и работала только пока тесты запускаются из него.
     * Теперь всё лежит в {@code src/test/resources} и путь не зависит от того, откуда
     * запустили сборку.
     */
    private static InputStream open(String resource) throws IOException {
        InputStream in = OzonTestFixtures.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("Фикстура не найдена в src/test/resources: " + resource);
        }
        return in;
    }

    static BigDecimal num(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
