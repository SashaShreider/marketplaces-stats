package ru.analizer.marketplace.ozon;

import ru.analizer.marketplace.AccrualDto;
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
     * @param resource путь относительно корня проекта; файлы в корне проекта
     *                 (например {@code example-2026-04-10.json}) и в {@code src/test/resources}
     */
    static List<AccrualDto> load(String resource) throws IOException {
        String raw = read(resource);
        var root = MAPPER.readTree(raw);
        List<AccrualDto> result = new ArrayList<>();
        for (var node : root.get("accruals")) {
            result.add(OzonMapper.toAccrualDto(
                    MAPPER.treeToValue(node, ru.analizer.marketplace.ozon.dto.FinanceAccrual.class),
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

    private static InputStream open(String resource) throws IOException {
        InputStream in = OzonTestFixtures.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(resource));
        }
        if (in == null) {
            throw new IOException("Фикстура не найдена: " + resource);
        }
        return in;
    }

    static BigDecimal num(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
