package ru.analizer.support;

import ru.analizer.integration.model.AccrualTypeInfo;
import ru.analizer.integration.ozon.OzonMapper;
import ru.analizer.integration.ozon.dto.finance.FinanceAccrual;
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
public final class FixtureAdapters {

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
    public static final String TYPES_FIXTURE = "fixtures/accrual-types-real.json";

    /** День → файл с ответом API. Заполняется конкретным тестом. */
    public static final Map<LocalDate, String> FIXTURES = new java.util.LinkedHashMap<>();

    /**
     * Ключ, который подставной адаптер считает неверным.
     *
     * <p>Нужен, чтобы проверить путь отказа: приложение обязано вернуть 400 и не
     * оставить после себя аккаунт. Настоящий OZON такие ключи отвергает сам.
     */
    public static final String REJECTED_KEY = "reject-me";

    private static BulkFixtureAdapter bulk;

    private FixtureAdapters() {
    }

    public static void setBulkAdapter(BulkFixtureAdapter adapter) {
        bulk = adapter;
    }

    public static BulkFixtureAdapter bulkAdapter() {
        return bulk;
    }

    public static void clearAll() {
        FIXTURES.clear();
        if (bulk != null) {
            bulk.reset();
        }
    }

    static String typesJson() {
        return readText(TYPES_FIXTURE);
    }

    public static List<AccrualTypeInfo> types() {
        List<AccrualTypeInfo> cached = typesCache;
        if (cached == null) {
            cached = parseTypes();
            typesCache = cached;
        }
        return cached;
    }

    private static List<AccrualTypeInfo> parseTypes() {
        try {
            var root = MAPPER.readTree(typesJson());
            List<AccrualTypeInfo> types = new ArrayList<>();
            for (var node : root.get("accrual_types")) {
                var type = MAPPER.treeToValue(node,
                        ru.analizer.integration.ozon.dto.finance.AccrualType.class);
                types.add(new AccrualTypeInfo(type.id(), type.name(), type.description()));
            }
            return List.copyOf(types);
        } catch (Exception e) {
            throw new IllegalStateException("Не удалось разобрать справочник типов", e);
        }
    }

    public static List<ru.analizer.integration.model.AccrualDto> parse(String resource) {
        return PARSED.computeIfAbsent(resource, FixtureAdapters::parseAccruals);
    }

    private static List<ru.analizer.integration.model.AccrualDto> parseAccruals(String resource) {
        var root = MAPPER.readTree(readText(resource));
        List<ru.analizer.integration.model.AccrualDto> result = new ArrayList<>();
        for (var node : root.get("accruals")) {
            result.add(OzonMapper.toAccrualDto(
                    MAPPER.treeToValue(node, FinanceAccrual.class), node.toString()));
        }
        return List.copyOf(result);
    }

    /** Сравнение JSON по значению: jsonb нормализует пробелы и порядок ключей. */
    public static boolean sameJson(String first, String second) {
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
    public static List<Long> soldSkus(String resource) {
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

    /**
     * Разобранные начисления по имени файла-фикстуры.
     *
     * <p>Разбор ответа — самая дорогая часть подготовки теста, а файлы фикстур не
     * меняются между тестами. Поэтому разобранное хранится один раз: без кэша
     * каждый тест разбирал бы заново десятки тысяч операций JSON.
     */
    private static final Map<String, List<ru.analizer.integration.model.AccrualDto>> PARSED =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Справочник типов начислений: разбирается один раз. */
    private static volatile List<AccrualTypeInfo> typesCache;

    private static String readText(String resource) {
        try (InputStream in = open(resource)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Не найдена фикстура " + resource, e);
        }
    }

    private static InputStream open(String resource) throws IOException {
        InputStream in = FixtureAdapters.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new IOException("Фикстура не найдена в src/test/resources: " + resource);
        }
        return in;
    }
}