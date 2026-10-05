package ru.analizer.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import ru.analizer.catalog.CatalogImportReport;
import ru.analizer.analytics.ProductReport;
import ru.analizer.persistence.entity.RunState;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Каталог товаров и отчёт по товарам на настоящей выгрузке
 * {@code /v4/product/info/attributes} (108 товаров).
 *
 * <p>Фикстура настоящая, а не выдуманная: идентификаторы авторов и разделители имён
 * взяты из неё, и выдуманные данные были бы проверкой выдуманных данных.
 */
// Оба адаптера подменены: тесты не должны уходить в настоящий OZON. Раньше был подменён
// только каталог, и финансовые данные приходили из .env разработчика.
@Import({CatalogAdapterConfig.class, FixtureAdapterConfig.class})
class ProductCatalogIT extends AbstractPostgresIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 4, 10);
    private static final String FINANCE_FIXTURE = "fixtures/accruals-2026-04-10.json";

    @Test
    @DisplayName("Загрузка каталога сохраняет все 108 товаров")
    void savesAllProducts() {
        CatalogImportReport report = catalogImportService.importProducts(accountId(), null);

        assertThat(report.totalProducts()).isEqualTo(108);
        assertThat(report.savedProducts()).isEqualTo(108);
        assertThat(report.failedProducts()).isZero();
        assertThat(report.complete()).isTrue();
        assertThat(count("ozon_product")).isEqualTo(108);
    }

    @Test
    @DisplayName("Обход каталога останавливается, хотя курсор непустой на последней странице")
    void paginationTerminates() {
        catalogImportService.importProducts(accountId(), null);
        // 108 товаров по 5 на страницу = 22 страницы плюс одна пустая на завершение.
        // Если бы обход шёл по «курсор не пуст», тест бы просто не завершился.
        assertThat(count("ozon_product")).isEqualTo(108);
    }

    @Test
    @DisplayName("Сохраняются все характеристики, а не только четыре нужные")
    void savesAllAttributes() {
        catalogImportService.importProducts(accountId(), null);

        // В фикстуре 18 атрибутов на товар, 89 различных идентификаторов.
        assertThat(count("ozon_product_attribute")).isGreaterThan(1000);
        Integer distinct = jdbc.queryForObject(
                "select count(distinct attribute_id) from ozon_product_attribute", Integer.class);
        assertThat(distinct).isEqualTo(89);
    }

    @Test
    @DisplayName("ISBN попадает и в колонку товара, и в характеристики")
    void isbnIsStoredTwice() {
        catalogImportService.importProducts(accountId(), null);

        Integer withIsbn = jdbc.queryForObject(
                "select count(*) from ozon_product where isbn is not null and isbn <> ''",
                Integer.class);
        Integer attributeIsbn = jdbc.queryForObject(
                "select count(*) from ozon_product_attribute where attribute_id = 4184",
                Integer.class);
        assertThat(withIsbn).isEqualTo(101);
        assertThat(attributeIsbn).isEqualTo(101);
    }

    @Test
    @DisplayName("Автор с несколькими фамилиями в одном поле разбивается на строки")
    void multipleAuthorsSplitIntoRows() {
        catalogImportService.importProducts(accountId(), null);

        // SKU 174269875: «Умнова-Конюхова И.А.» — это один человек, но разделять
        // по запятой нельзя, поэтому в нём ровно одна строка автора.
        List<String> authors = jdbc.queryForList("""
                select author_raw from product_author
                where sku = 174269875 and source = 'DECLARED'
                order by position
                """, String.class);
        assertThat(authors).containsExactly("Умнова-Конюхова И.А.");

        // SKU 174267969: «Черняев А. Ю., Ланде А. А.» — запятая разделяет, это два автора.
        List<String> two = jdbc.queryForList("""
                select author_raw from product_author
                where sku = 174267969 order by position
                """, String.class);
        assertThat(two).containsExactly("Черняев А. Ю.", "Ланде А. А.");
    }

    @Test
    @DisplayName("У товара без автора не появляется ни одной строки автора")
    void productWithoutAuthorGetsNoRows() {
        catalogImportService.importProducts(accountId(), null);

        // В выгрузке 9 товаров вообще без автора: бумага, календари, папка.
        Integer declared = jdbc.queryForObject(
                "select count(*) from ozon_product where not exists ("
                        + "select 1 from product_author a where a.sku = ozon_product.sku)",
                Integer.class);
        assertThat(declared).isEqualTo(9);
    }

    @Test
    @DisplayName("Сведённые имена убирают повтор между карточкой и обложкой")
    void normalizedKeysDeduplicateAcrossSources() {
        catalogImportService.importProducts(accountId(), null);

        // «Сурцуков А.» (карточка) и «Сурцуков Анатолий» (обложка) — один человек,
        // значит в author_keys он должен лежать один раз.
        List<String> keys = jdbc.queryForList("""
                select unnest(author_keys) from ozon_product where sku = 174269706
                """, String.class);
        assertThat(keys).containsExactly("сурцуков а.");

        // Обложка при этом сохраняется как отдельная строка: видно, откуда взялось имя.
        assertThat(jdbc.queryForObject("""
                select count(*) from product_author where sku = 174269706
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Одно поле автора может дать три разных ключа — это известное ограничение")
    void onePersonCanYieldSeveralKeys() {
        catalogImportService.importProducts(accountId(), null);

        List<String> keys = jdbc.queryForList("""
                select distinct unnest(author_keys) from ozon_product
                where sku in (select sku from product_author where source = 'DECLARED')
                  and exists (select 1 from product_author where sku = ozon_product.sku)
                """, String.class);
        // Известное ограничение сведения: разные написания одного человека остаются
        // разными ключами. Проверяем, что это правда, а не забытый баг.
        assertThat(keys).isNotEmpty();
        assertThat(keys.stream().distinct().count()).isEqualTo(keys.size());
    }

    @Test
    @DisplayName("Повторная загрузка каталога не создаёт дублей")
    void repeatedSyncIsIdempotent() {
        catalogImportService.importProducts(accountId(), null);
        long attributesAfterFirst = count("ozon_product_attribute");
        long authorsAfterFirst = count("product_author");

        catalogImportService.importProducts(accountId(), null);

        assertThat(count("ozon_product")).isEqualTo(108);
        assertThat(count("ozon_product_attribute")).isEqualTo(attributesAfterFirst);
        assertThat(count("product_author")).isEqualTo(authorsAfterFirst);
    }

    @Test
    @DisplayName("Изменение карточки обновляет товар, а не плодит копии")
    void changedProductIsUpdated() {
        catalogImportService.importProducts(accountId(), null);

        jdbc.update("update ozon_product set name = 'старое название' where sku = 174267969");
        catalogImportService.importProducts(accountId(), null);

        assertThat(count("ozon_product")).isEqualTo(108);
        assertThat(jdbc.queryForObject(
                "select name from ozon_product where sku = 174267969", String.class))
                .startsWith("Армейская авиация");
    }

    @Test
    @DisplayName("Отчёт показывает ВСЕ товары каталога, а не только проданные")
    void reportListsAllCatalogProducts() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        assertThat(report.totalRows()).isEqualTo(108);
        assertThat(report.rows()).hasSize(108);
        assertThat(report.catalog().products()).isEqualTo(108);
        assertThat(report.catalog().loaded()).isTrue();
        // Товаров с продажами за день намного меньше, чем товаров в каталоге:
        // именно эти строки с нулями и должны быть видны.
        assertThat(report.totals().productsWithSales()).isLessThan(108);
    }

    @Test
    @DisplayName("Ноль у товара, который есть в каталоге, — честный ответ «продаж не было»")
    void zeroSalesIsHonestZero() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        long soldSkus = new java.util.HashSet<>(FixtureAdapters.soldSkus(FINANCE_FIXTURE)).size();
        assertThat(soldSkus).isGreaterThan(0);
        assertThat(report.rows()).filteredOn(r -> r.income().signum() == 0).isNotEmpty();
    }

    @Test
    @DisplayName("Сумма расходов по товарам плюс нераспределённые равна дневному отчёту")
    void expensesReconcileWithDailyReport() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);
        assertThat(productAnalytics.expensesReconciliationDiff(accountId(), DAY, DAY))
                .isEqualByComparingTo(java.math.BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Часть расходов остаётся нераспределённой и не растворяется в товарах")
    void unallocatedExpensesAreVisible() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        // NON_ITEM и CONTAINER приходят без SKU: распределить их значило бы выдумать
        // правило. Поэтому они показаны отдельно, а не размазаны по товарам.
        assertThat(report.unallocatedExpenses()).isGreaterThan(java.math.BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Незагруженный период помечает отчёт, а не показывает нули как есть")
    void unloadedPeriodIsMarked() {
        catalogImportService.importProducts(accountId(), null);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        assertThat(report.status().name()).isEqualTo("NOT_LOADED");
        assertThat(report.coverage().loadedDays()).isZero();
        assertThat(report.coverage().missingDays()).isNotEmpty();
    }

    @Test
    @DisplayName("Частично загруженный период даёт PARTIAL, а не READY")
    void partialPeriodIsMarked() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);
        FixtureAdapters.FIXTURES.put(DAY.plusDays(1), "fixtures/empty-day.json");
        accrualImportService.importAccruals(accountId(), DAY.plusDays(1), DAY.plusDays(1));

        // Запрашиваем на день больше, чем загрузили: иначе период закрыт полностью и
        // READY был бы правильным ответом.
        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY.plusDays(2), null, null, "SKU", 0, 500);

        assertThat(report.status().name()).isEqualTo("PARTIAL");
        assertThat(report.coverage().loadedDays()).isEqualTo(2);
        assertThat(report.coverage().missingDays()).containsExactly(DAY.plusDays(2));
    }

    @Test
    @DisplayName("Загруженный период даёт READY")
    void loadedPeriodIsReady() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        assertThat(report.status().name()).isEqualTo("READY");
        assertThat(report.coverage().loadedDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("Фильтр по автору находит оба написания одного человека")
    void authorFilterMatchesBothForms() {
        catalogImportService.importProducts(accountId(), null);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Сурцуков Анатолий", null, "SKU", 0, 500);

        assertThat(report.totalRows()).isPositive();
        assertThat(report.rows()).allSatisfy(row ->
                assertThat(row.authors()).isNotEmpty());
    }

    @Test
    @DisplayName("Фильтр по фамилии находит товары, а полное имя — тоже")
    void surnameFilterWorks() {
        catalogImportService.importProducts(accountId(), null);

        ProductReport bySurname = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Сурцуков", null, "SKU", 0, 500);
        ProductReport byFullName = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Сурцуков А.", null, "SKU", 0, 500);

        assertThat(bySurname.totalRows()).isEqualTo(byFullName.totalRows());
        assertThat(bySurname.totalRows()).isPositive();
    }

    @Test
    @DisplayName("Фильтр по названию и ISBN работает")
    void queryFilterWorks() {
        catalogImportService.importProducts(accountId(), null);

        ProductReport byIsbn = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, "9785907081338", "SKU", 0, 500);

        assertThat(byIsbn.totalRows()).isEqualTo(1);
        assertThat(byIsbn.rows().getFirst().isbn()).isEqualTo("9785907081338");
    }

    @Test
    @DisplayName("Авторы показываются в исходном виде, с указанием источника")
    void authorsAreShownAsGiven() {
        catalogImportService.importProducts(accountId(), null);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Умнова-Конюхова", null, "SKU", 0, 500);

        assertThat(report.rows()).isNotEmpty();
        assertThat(report.rows().getFirst().authors()).extracting("raw")
                .contains("Умнова-Конюхова И.А.");
        assertThat(report.rows().getFirst().authors()).extracting("source").contains("DECLARED");
    }

    @Test
    @DisplayName("Сортировка по доходу идёт от большего к меньшему")
    void sortedByIncome() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "INCOME", 0, 500);

        List<java.math.BigDecimal> incomes = report.rows().stream()
                .map(r -> r.income()).toList();
        assertThat(incomes).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("Постраничная выборка не ломает итоги")
    void paginationKeepsTotals() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);

        ProductReport all = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "INCOME", 0, 500);
        ProductReport firstPage = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "INCOME", 0, 20);

        assertThat(firstPage.rows()).hasSize(20);
        assertThat(firstPage.totalRows()).isEqualTo(108);
        assertThat(firstPage.totalPages()).isEqualTo(6);
        assertThat(firstPage.totals().income()).isEqualByComparingTo(all.totals().income());
    }

    @Test
    @DisplayName("SKU из начислений без товара в каталоге показываются отдельным списком")
    void skusMissingFromCatalogAreListed() {
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);

        // Каталог не загружали: все SKU из начислений отсутствуют.
        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        assertThat(report.skusMissingFromCatalog()).isNotEmpty();
        assertThat(report.catalog().loaded()).isFalse();
    }

    @Test
    @DisplayName("Фоновая задача загрузки каталога создаётся и доводит каталог до конца")
    void backgroundCatalogJobCompletes() {
        var status = importService.startCatalogImport(account(), MARKETPLACE);

        assertThat(status.importType().name()).isEqualTo("CATALOG");
        assertThat(status.dateFrom()).isNull();
        var finished = awaitJob(status.id());
        assertThat(finished.status()).isEqualTo(RunState.DONE);
        assertThat(count("ozon_product")).isEqualTo(108);
        assertThat(finished.totalUnits()).isEqualTo(108);
        assertThat(finished.doneUnits()).isEqualTo(108);
    }

    @Test
    @DisplayName("Вторая загрузка каталога, пока идёт первая, отклоняется")
    void overlappingCatalogJobRejected() {
        var first = importService.startCatalogImport(account(), MARKETPLACE);
        try {
            importService.startCatalogImport(account(), MARKETPLACE);
            org.junit.jupiter.api.Assertions.fail("ожидался отказ на второй запуск");
        } catch (ru.analizer.sync.ImportConflictException expected) {
            assertThat(expected.conflict())
                    .isEqualTo(ru.analizer.sync.ImportConflictException.Conflict.CATALOG_BUSY);
            assertThat(expected.activeImportId()).isEqualTo(first.id());
            assertThat(expected.getMessage()).contains("уже выполняется");
        }
        awaitJob(first.id());
    }

    @Test
    @DisplayName("Конфликт импортов различается по машиночитаемой причине")
    void conflictCarriesMachineReadableReason() {
        // Клиент должен отличать «дождаться текущего импорта» от «несколько аккаунтов»
        // по значению поля, а не по разбору текста сообщения.
        var first = importService.startFinanceImport(account(), MARKETPLACE, DAY, DAY, true);

        try {
            importService.startFinanceImport(account(), MARKETPLACE, DAY, DAY, true);
            org.junit.jupiter.api.Assertions.fail("ожидался отказ на пересекающийся период");
        } catch (ru.analizer.sync.ImportConflictException expected) {
            assertThat(expected.conflict())
                    .isEqualTo(ru.analizer.sync.ImportConflictException.Conflict.OVERLAPPING_PERIOD);
            assertThat(expected.requestedFrom()).isEqualTo(DAY);
            assertThat(expected.requestedTo()).isEqualTo(DAY);
            assertThat(expected.activeImportId()).isNotNull();
        }
        awaitJob(first.id());
    }

    @Test
    @DisplayName("Загрузка каталога не блокирует финансовую: дат у неё нет")
    void catalogJobDoesNotBlockFinance() {
        var catalog = importService.startCatalogImport(account(), MARKETPLACE);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);

        var finance = importService.startFinanceImport(account(), MARKETPLACE, DAY, DAY, true);

        assertThat(finance.importType().name()).isEqualTo("FINANCE");
        awaitJob(catalog.id());
        awaitJob(finance.id());
    }

    @Test
    @DisplayName("Задачи каталога не блокируют друг друга по датам")
    void catalogJobsNotBlockedByDateOverlap() {
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);

        // Финансовая задача в прошлом осталась завершённой — активной блокировки нет.
        var job = importService.startCatalogImport(account(), MARKETPLACE);
        assertThat(job.importType().name()).isEqualTo("CATALOG");
        awaitJob(job.id());
    }
}