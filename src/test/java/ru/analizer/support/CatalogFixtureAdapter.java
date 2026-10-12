package ru.analizer.support;

import ru.analizer.integration.ProductCatalogAdapter;
import ru.analizer.integration.model.CatalogPage;
import ru.analizer.integration.model.ProductEntry;

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
public final class CatalogFixtureAdapter implements ProductCatalogAdapter {

    /** Настоящий ответ {@code POST /v4/product/info/attributes}. */
    static final String FIXTURE = "fixtures/product-attributes-v4.json";

    /** Сколько товаров отдавать за страницу по умолчанию. */
    private static final int PAGE = 5;

    private static List<ProductEntry> cached;

    private int calls;

    private CatalogFixtureAdapter() {
    }

    public static CatalogFixtureAdapter create() {
        return new CatalogFixtureAdapter();
    }

    /** Сколько раз адаптер был опрошен — по этому видно, сколько страниц обошли. */
    int calls() {
        return calls;
    }

    /**
     * SKU, которого нет в обходе, но который находится точечным запросом.
     *
     * <p>Так ведёт себя настоящий OZON с архивным товаром: при {@code visibility=ALL}
     * его нет, а запрос по конкретным SKU его находит. Без такой возможности в
     * заглушке проверить догрузку архивных было бы нечем — обход всегда отдавал бы
     * всё, и код, считающий товар архивным по отсутствию в обходе, проходил бы тесты,
     * ни разу не проверившись.
     *
     * <p>Общий статический: тесты одного класса делят контекст Spring, а экземпляр
     * заглушки из него достать нельзя — так же сделан и кэш товаров.
     */
    private static volatile Long hiddenSku;

    /** Прячет товар из обхода каталога: он останется доступен только точечно. */
    public static void hideSku(long sku) {
        hiddenSku = sku;
    }

    /** Возвращает все товары обходу обратно. Вызывать в конце каждого теста. */
    public static void showAll() {
        hiddenSku = null;
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

    /**
     * Архивный товар.
     *
     * <p>Настоящий обход при {@code visibility=ALL} его не возвращает, поэтому и в
     * фикстуре он не попадает в {@link #page}: товар появляется только здесь, по
     * точному запросу. Иначе тест проходил бы, а рабочая загрузка архивные товары
     * не догружала бы.
     */
    @Override
    public List<ProductEntry> fetchArchivedBySku(ru.analizer.account.domain.MarketplaceCredentials credentials, List<String> skus) {
        List<ProductEntry> all = products();
        if (skus == null || skus.isEmpty()) {
            return List.of();
        }
        List<String> wanted = skus.stream().map(String::valueOf).toList();
        List<ProductEntry> found = all.stream()
                .filter(p -> wanted.contains(String.valueOf(p.sku())))
                .toList();
        List<ProductEntry> archived = new ArrayList<>(found.size());
        for (ProductEntry entry : found) {
            archived.add(new ProductEntry(entry.sku(), entry.ozonProductId(), entry.offerId(),
                    entry.name(), entry.barcode(), entry.typeId(), entry.descriptionCategoryId(),
                    entry.primaryImage(), entry.weightGrams(), entry.widthMm(), entry.heightMm(),
                    entry.depthMm(), entry.modelId(), entry.attributes(), entry.rawJson(), true));
        }
        return archived;
    }

    private CatalogPage page(String lastId, int limit) {
        calls++;
        // Обход отдаёт всё, кроме скрытого: скрытый товар в нём не появляется никогда,
        // ровно как архивный у настоящего OZON.
        List<ProductEntry> visible = products();
        if (hiddenSku != null) {
            visible = visible.stream()
                    .filter(p -> p.sku() != hiddenSku)
                    .toList();
        }
        int from = indexAfter(lastId);
        int size = limit > 0 ? limit : PAGE;
        int to = Math.min(from + size, visible.size());
        List<ProductEntry> slice = from >= visible.size() ? List.of() : visible.subList(from, to);
        // Курсор непустой и на последней странице — как у настоящего API.
        return new CatalogPage(slice, visible.size(), cursorFor(slice));
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
                        ru.analizer.integration.ozon.dto.catalog.ProductInfoV4.class);
                if (product.sku() == null) {
                    continue;
                }
                // Разбор тот же, что в бою: тест обязан проверять рабочий код, а не его
                // копию. Иначе исправление в проде останется непроверенным.
                result.add(ru.analizer.integration.ozon.OzonProductMapper
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

    /** Сколько авторов разбирается у каждого товара — для проверок ожиданий. */
    static Map<Long, Integer> declaredAuthorCounts() {
        Map<Long, Integer> counts = new java.util.HashMap<>();
        for (ProductEntry product : products()) {
            // Все значения атрибута, а не первое: «Автор на обложке» приходит массивом,
            // и взятие одного значения занизило бы ожидаемое число авторов.
            counts.merge(product.sku(),
                    ru.analizer.catalog.domain.AuthorExtractor
                            .extract(product.attributeValues(4182L), true).size(),
                    Integer::sum);
        }
        return counts;
    }
}