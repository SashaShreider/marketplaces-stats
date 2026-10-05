package ru.analizer.integration.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import ru.analizer.integration.CatalogPager;
import ru.analizer.integration.model.CatalogPage;
import ru.analizer.integration.model.ProductEntry;
import ru.analizer.integration.ProductCatalogAdapter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Остановка обхода страниц каталога.
 *
 * <p>Здесь проверяется поведение, из-за которого наивная реализация зависает: OZON
 * возвращает непустой {@code last_id} даже на последней странице. На настоящем ответе
 * это подтверждено — курсор указывает на последний товар страницы.
 *
 * <p>Каждый способ остановки проверяется отдельно: для этого в заглушке задаётся
 * {@code total}, который либо совпадает с числом товаров, либо заведомо недостижим.
 * Иначе одна проверка перехватывала бы другую и тест ничего бы не доказывал.
 */
class CatalogPagerTest {

    /** Реквизиты в тестах пагинации не используы: клиент подменён заглушкой. */
    private static final ru.analizer.account.domain.MarketplaceCredentials CREDENTIALS =
            new ru.analizer.account.domain.MarketplaceCredentials("1154", "test-api-key");

    @Test
    @DisplayName("Обход заканчивается по числу собранных товаров, хотя курсор непустой")
    void terminatesWhenTotalReached() {
        Stub adapter = new Stub(products(7), 7, false);

        List<ProductEntry> collected = CatalogPager.fetchAll(adapter, CREDENTIALS, 3);

        assertThat(collected).hasSize(7);
        // 3 + 3 + 1: на четвёртую страницу не пошли, потому что собрано всё.
        assertThat(adapter.calls).isEqualTo(3);
    }

    @Test
    @DisplayName("Обход заканчивается пустой страницей, даже если total недостижим")
    void terminatesOnEmptyPageWhenTotalUnreachable() {
        // total заведомо больше, чем отдаёт заглушка: работать может только пустая страница.
        Stub adapter = new Stub(products(7), 100, false);

        List<ProductEntry> collected = CatalogPager.fetchAll(adapter, CREDENTIALS, 3);

        assertThat(collected).hasSize(7);
        // 3 + 3 + 1 и одна пустая страница, на которой обход и останавливается.
        assertThat(adapter.calls).isEqualTo(4);
    }

    @Test
    @DisplayName("Курсор, не сдвинувшийся, останавливает обход")
    void stopsWhenCursorDoesNotMove() {
        Stub adapter = new Stub(products(5), 100, true);

        List<ProductEntry> collected = CatalogPager.fetchAll(adapter, CREDENTIALS, 10);

        // Первый ответ принёс товары, но вернул прежний курсор — идти некуда,
        // сколько бы страниц мы ни запросили.
        assertThat(collected).isNotEmpty();
        assertThat(adapter.calls).isEqualTo(2);
    }

    @Test
    @DisplayName("Пустая страница завершает обход")
    void stopsOnEmptyPage() {
        Stub adapter = new Stub(List.of(), 0, false);

        assertThat(CatalogPager.fetchAll(adapter, CREDENTIALS, 10)).isEmpty();
        assertThat(adapter.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("Повторяющиеся товары не попадают в результат дважды")
    void duplicatesRemoved() {
        ProductEntry once = products(1).getFirst();
        Stub adapter = new Stub(List.of(once, once, once), 100, false);

        assertThat(CatalogPager.fetchAll(adapter, CREDENTIALS, 10)).hasSize(1);
    }

    private static List<ProductEntry> products(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(i -> new ProductEntry(1000L + i, 1000L + i, "offer-" + i,
                        "Товар " + i, null, 1L, 1L, null, null, null, null, null, null,
                        List.of(), "{}"))
                .toList();
    }

    /** Заглушка адаптера с управляемым курсором и недостижимым, если нужно, total. */
    private static final class Stub implements ProductCatalogAdapter {

        private final List<ProductEntry> all;
        private final int reportedTotal;
        /** Когда задано, курсор не сдвигается — проверяем второй способ остановки. */
        private final boolean frozenCursor;
        private int from;
        private int calls;

        private Stub(List<ProductEntry> all, int reportedTotal, boolean frozenCursor) {
            this.all = all;
            this.reportedTotal = reportedTotal;
            this.frozenCursor = frozenCursor;
        }

        @Override
        public String marketplaceCode() {
            return "STUB";
        }

        @Override
        public CatalogPage fetchProducts(ru.analizer.account.domain.MarketplaceCredentials credentials, String lastId, int limit) {
            calls++;
            if (from >= all.size()) {
                return new CatalogPage(List.of(), reportedTotal, "cursor-" + from);
            }
            int to = Math.min(from + limit, all.size());
            List<ProductEntry> slice = all.subList(from, to);
            from = frozenCursor ? 0 : to;
            // Курсор всегда непустой, как у настоящего метода.
            return new CatalogPage(slice, reportedTotal, "cursor-" + to);
        }

        @Override
        public CatalogPage fetchProductsBySku(ru.analizer.account.domain.MarketplaceCredentials credentials, List<String> skus) {
            return new CatalogPage(List.of(), 0, "");
        }
    }
}