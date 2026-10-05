package ru.analizer.integration;

import ru.analizer.marketplace.CatalogPage;
import ru.analizer.marketplace.ProductAttributeEntry;
import ru.analizer.marketplace.ProductCatalogAdapter;
import ru.analizer.marketplace.ProductEntry;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Каталог товаров из сохранённого ответа OZON.
 *
 * <p>Отдаёт товары постранично и повторяет поведение настоящего API в двух местах,
 * на которых ломается наивная реализация:
 * <ul>
 *   <li>курсор {@code last_id} непустой даже на последней странице и кодирует
 *       {@code product_id} последнего товара, а не следующий; в Base64 это
 *       {@code [id,id]};</li>
 *   <li>товар без {@code sku} в каталог не попадает.</li>
 * </ul>
 * Если бы фикстура отдавала пустой курсор в конце, тесты прошли бы, а рабочая загрузка
 * зависла бы.
 */
final class CatalogFixtureAdapter implements ProductCatalogAdapter {

    /** Настоящий ответ {@code POST /v4/product/info/attributes}. */
    static final String FIXTURE = "fixtures/product-attributes-v4.json";

    /** Сколько товаров отдавать за страницу по умолчанию. */
    private static final int PAGE = 5;

    private static List<ProductEntry> cached;

    private int calls;

    private CatalogFixtureAdapter() {
    }

    static CatalogFixtureAdapter create() {
        return new CatalogFixtureAdapter();
    }

    /** Сколько раз адаптер был опрошен — по этому видно, сколько страниц обошли. */
    int calls() {
        return calls;
    }

    @Override
    public String marketplaceCode() {
        return "OZON";
    }

    @Override
    public CatalogPage fetchProducts(ru.analizer.account.domain.MarketplaceCredentials credentials, String lastId, int limit) {
        return page(lastId, limit);
    }

    @Override
    public CatalogPage fetchProductsBySku(ru.analizer.account.domain.MarketplaceCredentials credentials, List<String> skus) {
        List<ProductEntry> all = products();
        if (skus == null || skus.isEmpty()) {
            return new CatalogPage(List.of(), 0, "");
        }
        List<String> wanted = skus.stream().map(String::valueOf).toList();
        List<ProductEntry> filtered = all.stream()
                .filter(p -> wanted.contains(String.valueOf(p.sku())))
                .toList();
        return new CatalogPage(filtered, filtered.size(), cursorFor(filtered));
    }

    private CatalogPage page(String lastId, int limit) {
        calls++;
        List<ProductEntry> all = products();
        int from = indexAfter(lastId);
        int size = limit > 0 ? limit : PAGE;
        int to = Math.min(from + size, all.size());
        List<ProductEntry> slice = from >= all.size() ? List.of() : all.subList(from, to);
        // Курсор непустой и на последней странице — как у настоящего API.
        return new CatalogPage(slice, all.size(), cursorFor(slice));
    }

    private static int indexAfter(String lastId) {
        if (lastId == null || lastId.isBlank()) {
            return 0;
        }
        String decoded = new String(Base64.getDecoder().decode(lastId), StandardCharsets.UTF_8);
        // Формат ответа OZON: [product_id,product_id]
        String id = decoded.replace("[", "").replace("]", "").split(",")[0].trim();
        List<ProductEntry> all = products();
        for (int i = 0; i < all.size(); i++) {
            if (String.valueOf(all.get(i).ozonProductId()).equals(id)) {
                return i + 1;
            }
        }
        return 0;
    }

    private static String cursorFor(List<ProductEntry> slice) {
        if (slice.isEmpty()) {
            return "";
        }
        Long id = slice.getLast().ozonProductId();
        return Base64.getEncoder().encodeToString(
                ("[" + id + "," + id + "]").getBytes(StandardCharsets.UTF_8));
    }

    static synchronized List<ProductEntry> products() {
        if (cached != null) {
            return cached;
        }
        try (InputStream in = open()) {
            tools.jackson.databind.json.JsonMapper mapper = tools.jackson.databind.json.JsonMapper.builder()
                    .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
            var root = mapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<ProductEntry> result = new ArrayList<>();
            for (var node : root.get("result")) {
                var product = mapper.treeToValue(node,
                        ru.analizer.marketplace.ozon.dto.ProductInfoV4.class);
                if (product.sku() == null) {
                    continue;
                }
                // Разбор тот же, что в бою: тест обязан проверять рабочий код, а не его
                // копию. Иначе исправление в проде останется непроверенным.
                result.add(ru.analizer.marketplace.ozon.OzonProductMapper
                        .toProductEntry(product, node.toString()));
            }
            cached = List.copyOf(result);
            return cached;
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать фикстуру каталога", e);
        }
    }

    private static Integer toGrams(Integer value, String unit) {
        if (value == null) {
            return null;
        }
        return "kg".equalsIgnoreCase(unit) ? value * 1000 : value;
    }

    private static Integer toMillimetres(Integer value, String unit) {
        if (value == null) {
            return null;
        }
        return "cm".equalsIgnoreCase(unit) ? value * 10 : value;
    }

    private static InputStream open() throws IOException {
        InputStream in = CatalogFixtureAdapter.class.getClassLoader().getResourceAsStream(FIXTURE);
        if (in == null) {
            throw new IOException("Фикстура не найдена в src/test/resources: " + FIXTURE);
        }
        return in;
    }

    /** Сколько уникальных id авторов встречается в фикстуре — для проверок ожиданий. */
    static Map<Long, Integer> declaredAuthorCounts() {
        Map<Long, Integer> counts = new java.util.HashMap<>();
        for (ProductEntry product : products()) {
            String author = product.attributeValue(4182L);
            counts.merge(product.sku(), author == null ? 0 : ru.analizer.catalog.AuthorExtractor
                    .extract(author, true).size(), Integer::sum);
        }
        return counts;
    }
}