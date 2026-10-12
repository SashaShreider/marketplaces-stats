package ru.analizer.integration.ozon;

import org.springframework.stereotype.Component;
import ru.analizer.account.domain.MarketplaceCredentials;
import ru.analizer.integration.ProductCatalogAdapter;
import ru.analizer.integration.model.CatalogPage;
import ru.analizer.integration.model.ProductEntry;
import ru.analizer.integration.ozon.dto.catalog.ProductAttributesFilter;
import ru.analizer.integration.ozon.dto.catalog.ProductAttributesRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Источник характеристик товаров OZON.
 *
 * <p>Знает только одно: каким фильром берутся ВСЕ товары аккаунта.
 * Всё остальное — заголовки, повторы, разбор ошибок, разбор ответа — в
 * {@link OzonClient}, как и у финансовых методов.
 */
@Component
public class OzonCatalogAdapter implements ProductCatalogAdapter {

    /**
     * Показываем все товары, включая скрытые: отчёт по товарам должен быть полным,
     * иначе продавец не увидит то, что перестало продаваться.
     *
     * <p>Но не включая архивные: по документации OZON {@code ALL} — это «все товары,
     * <b>кроме архивных</b>». Поэтому обход сам по себе архивные товары никогда не
     * увидит, сколько его ни повторяй, и они догружаются отдельно — см.
     * {@link #fetchArchivedBySku}.
     *
     * <p>Значение — строка, а не массив: {@code ["ALL"]} OZON отклоняет с ошибкой
     * {@code invalid value for enum field visibility}.
     */
    private static final String VISIBILITY_ALL = "ALL";

    /** Максимум по спецификации — 1000 за один ответ. */
    private static final int DEFAULT_LIMIT = 1000;

    private final OzonClient client;

    public OzonCatalogAdapter(OzonClient client) {
        this.client = client;
    }

    @Override
    public String marketplaceCode() {
        return OzonAdapter.MARKETPLACE_CODE;
    }

    @Override
    public CatalogPage fetchProducts(MarketplaceCredentials credentials, String lastId, int limit) {
        ProductAttributesFilter filter = new ProductAttributesFilter(
                VISIBILITY_ALL, null, null, null);
        return client.getProductAttributes(credentials,
                new ProductAttributesRequest(filter, lastId == null ? "" : lastId, limit, null));
    }

    @Override
    public CatalogPage fetchProductsBySku(MarketplaceCredentials credentials, List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return new CatalogPage(List.of(), 0, "");
        }
        return client.getProductAttributes(credentials, new ProductAttributesRequest(
                ProductAttributesFilter.bySku(skus), "", DEFAULT_LIMIT, null));
    }

    /**
     * Карточки товаров, которых не хватило после обхода, помеченные как архивные.
     *
     * <p>Вызывается только когда основной обход нашёл SKU из начислений не у себя.
     * Тогда причина обычно одна: товар снят с продажи и убран в архив, а
     * {@code visibility=ALL} архивные не возвращает — это значит буквальное
     * «все товары, кроме архивных» из документации.
     *
     * <p>Шаг двухчастный, и это не перестраховка. Спросить {@code /v3/product/list},
     * какие именно SKU архивные, можно одним запросом; а вот полноценных
     * характеристик этот метод не отдаёт, и карточка для отчёта по товарам без них
     * была бы пустой строкой без автора. Поэтому: сначала дешёвый запрос, чтобы
     * отсеять SKU, которых нет и в архиве, затем обычная догрузка карточек по тем,
     * что действительно нашлись.
     *
     * @param skus SKU из начислений, отсутствующие в каталоге
     * @return карточки с признаком архивности; пусто, если таких товаров нет
     */
    @Override
    public List<ProductEntry> fetchArchivedBySku(MarketplaceCredentials credentials, List<String> skus) {
        if (skus == null || skus.isEmpty()) {
            return List.of();
        }
        List<Long> found = client.skusArchivedOrHidden(credentials, skus);
        if (found.isEmpty()) {
            return List.of();
        }
        Set<String> archivedSkus = found.stream().map(String::valueOf).collect(Collectors.toSet());
        CatalogPage page = fetchProductsBySku(credentials, new ArrayList<>(archivedSkus));
        List<ProductEntry> archived = new ArrayList<>();
        for (ProductEntry entry : page.products()) {
            archived.add(new ProductEntry(entry.sku(), entry.ozonProductId(), entry.offerId(),
                    entry.name(), entry.barcode(), entry.typeId(), entry.descriptionCategoryId(),
                    entry.primaryImage(), entry.weightGrams(), entry.widthMm(), entry.heightMm(),
                    entry.depthMm(), entry.modelId(), entry.attributes(), entry.rawJson(), true));
        }
        return archived;
    }
}