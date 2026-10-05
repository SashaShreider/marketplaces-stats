package ru.analizer.integration;

import ru.analizer.integration.model.AccrualTypeInfo;
import ru.analizer.integration.ozon.OzonMapper;
import ru.analizer.integration.ozon.dto.finance.FinanceAccrual;
import ru.analizer.sync.infrastructure.entity.Posting;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Разбор сохранённых ответов OZON для тестов.
 *
 * <p>Разбор единственный и общий: тесты должны видеть ровно те же данные, что и боевое
 * приложение, иначе проверки разбора ничего не значат.
 */
final class FixtureAdapters {

    private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /**
     * Реальный справочник типов начислений.
     *
     * <p>Берётся из сохранённого ответа {@code /v1/finance/accrual/types}, а не пишется
     * руками: угадывать названия типов бессмысленно, OZON может их менять, и именно
     * поэтому бизнес-логика не должна опираться на конкретные {@code type_id}.
     */
    static final String TYPES_FIXTURE = "fixtures/accrual-types-real.json";

    /** День → файл с ответом API. Заполняется конкретным тестом. */
    static final Map<LocalDate, String> FIXTURES = new java.util.LinkedHashMap<>();

    /**
     * Ключ, который подставной адаптер считает неверным.
     *
     * <p>Нужен, чтобы проверить путь отказа: приложение обязано вернуть 400 и не
     * оставить после себя аккаунт. Настоящий OZON такие ключи отвергает сам.
     */
    static final String REJECTED_KEY = "reject-me";

    private static BulkFixtureAdapter bulk;

    private FixtureAdapters() {
    }

    static void setBulkAdapter(BulkFixtureAdapter adapter) {
        bulk = adapter;
    }

    static BulkFixtureAdapter bulkAdapter() {
        return bulk;
    }

    static void clearAll() {
        FIXTURES.clear();
        if (bulk != null) {
            bulk.reset();
        }
    }

    static String typesJson() {
        try (InputStream in = open(TYPES_FIXTURE)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не найдена фикстура справочника типов", e);
        }
    }

    static List<AccrualTypeInfo> types() {
        try {
            var root = MAPPER.readTree(typesJson());
            List<AccrualTypeInfo> types = new ArrayList<>();
            for (var node : root.get("accrual_types")) {
                var type = MAPPER.treeToValue(node,
                        ru.analizer.integration.ozon.dto.finance.AccrualType.class);
                types.add(new AccrualTypeInfo(type.id(), type.name(), type.description()));
            }
            return types;
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось разобрать справочник типов", e);
        }
    }

    static List<ru.analizer.integration.model.AccrualDto> parse(String resource) {
        try (InputStream in = open(resource)) {
            var root = MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<ru.analizer.integration.model.AccrualDto> result = new ArrayList<>();
            for (var node : root.get("accruals")) {
                result.add(OzonMapper.toAccrualDto(
                        MAPPER.treeToValue(node, FinanceAccrual.class), node.toString()));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать фикстуру " + resource, e);
        }
    }

    /** Сравнение JSON по значению: jsonb нормализует пробелы и порядок ключей. */
    static boolean sameJson(String first, String second) {
        try {
            return java.util.Objects.equals(MAPPER.readTree(first), MAPPER.readTree(second));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * SKU, которые реально встречаются в начислениях за файл-фикстуру.
     *
     * <p>Нужно, чтобы отчёт по товарам можно было проверить на настоящих продажах, а не
     * на выдуманных: у SKU из POSTING и у SKU из ITEM начисления разные.
     */
    static List<Long> soldSkus(String resource) {
        List<Long> skus = new ArrayList<>();
        for (ru.analizer.integration.model.AccrualDto accrual : parse(resource)) {
            if (accrual.posting() != null) {
                accrual.posting().products().forEach(p -> skus.add(p.sku()));
            }
            if (accrual.itemFees() != null) {
                accrual.itemFees().forEach(f -> skus.add(f.sku()));
            }
        }
        return skus;
    }

    private static InputStream open(String resource) throws IOException {
        InputStream in = FixtureAdapters.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("Фикстура не найдена в src/test/resources: " + resource);
        }
        return in;
    }
}