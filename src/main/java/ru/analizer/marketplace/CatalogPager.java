package ru.analizer.marketplace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Обход страниц каталога товаров.
 *
 * <p><b>Почему здесь нельзя проверять «курсор пуст».</b> У
 * {@code /v4/product/info/attributes} курсор {@code last_id} приходит непустым даже на
 * последней странице: он указывает на последний товар этой страницы. Это проверено на
 * настоящем ответе — на выгрузке всех 108 товаров пришёл
 * {@code last_id = WzYzMzA1Mjc3NzEsNjMzMDUyNzc3MV0=}, который декодируется в
 * {@code [6330527771, 6330527771]}, а 6330527771 это максимальный {@code id} в выдаче.
 * Цикл «пока курсор не пуст» в этом случае не завершился бы никогда: последняя
 * страница запрашивалась бы снова и снова.
 *
 * <p>Поэтому цикл останавливается на любом из четырёх условий:
 * <ol>
 *   <li>страница пришла пустой — данных больше нет;</li>
 *   <li>курсор не сдвинулся — новый равен присланному, значит идти некуда;</li>
 *   <li>курсор пуст — страховка, если OZON всё-таки поменяет поведение;</li>
 *   <li>собрано не меньше, чем обещано в {@code total}, — экономит один запрос.</li>
 * </ol>
 * Плюс жёсткий предел числа страниц: последний рубеж, чтобы ошибка в любом из
 * условий не превратилась в зависшую загрузку.
 */
public final class CatalogPager {

    private static final Logger log = LoggerFactory.getLogger(CatalogPager.class);

    /** Предохранитель от бесконечного обхода. */
    private static final int MAX_PAGES = 1000;

    private CatalogPager() {
    }

    public static List<ProductEntry> fetchAll(ProductCatalogAdapter adapter,
                                        MarketplaceCredentials credentials,
                                        int limit) {
        List<ProductEntry> result = new ArrayList<>();
        Set<Long> seenSkus = new LinkedHashSet<>();
        String cursor = "";
        int pages = 0;

        while (pages < MAX_PAGES) {
            CatalogPage page = adapter.fetchProducts(credentials, cursor, limit);
            pages++;

            if (page.products().isEmpty()) {
                break;
            }
            for (ProductEntry product : page.products()) {
                if (seenSkus.add(product.sku())) {
                    result.add(product);
                }
            }
            log.debug("Страница каталога {}: товаров с {}, lastId={}", pages, page.products().size(), page.lastId());

            if (page.total() > 0 && result.size() >= page.total()) {
                log.debug("Каталог собран целиком: {} товаров", result.size());
                break;
            }
            String next = page.lastId() == null ? "" : page.lastId();
            if (next.isBlank()) {
                break;
            }
            if (next.equals(cursor)) {
                // Тот же курсор, что и до запроса: страница повторилась, дальше идти некуда.
                log.debug("Курсор не сдвинулся после {} страниц, обход остановлен", pages);
                break;
            }
            cursor = next;
        }

        log.info("Каталог {}: получено {} товаров за {} страниц(у)", adapter.marketplaceCode(), result.size(), pages);
        return result;
    }
}