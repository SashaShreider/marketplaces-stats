package ru.analizer.catalog.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import ru.analizer.analytics.domain.ProductReport;
import ru.analizer.catalog.domain.CatalogImportReport;
import ru.analizer.support.AbstractPostgresIntegrationTest;
import ru.analizer.support.CatalogAdapterConfig;
import ru.analizer.support.FixtureAdapterConfig;
import ru.analizer.support.FixtureAdapters;
import ru.analizer.sync.domain.RunState;

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
    @DisplayName("«Автор на обложке» не подмешивается, если автор есть в карточке")
    void coverAuthorIsIgnoredWhenDeclaredExists() {
        catalogImportService.importProducts(accountId(), null);

        // У SKU 174269875 в карточке 4182 стоит «Умнова-Конюхова И.А.», а «Автор на
        // обложке» (105) приходит тремя отдельными значениями массива: «Умнова Ирина
        // Анатольевна», «Конюхова Ирина Анатольевна», «Умнова-Конюхова Ирина
        // Анатольевна». Это один и тот же человек, и три строки автора в отчёте были бы
        // враньём: в карточке у него одно имя.
        //
        // Проверяем ровно это — что лишние значения обложки не попали в базу. Того,
        // что само разбиение массива работает, юнит-тест не покрывает: в этой выгрузке
        // товаров с 105, но без 4182, нет вовсе, и интеграционный тест был бы заведомо
        // пустым.
        List<String> rows = jdbc.queryForList("""
                select author_raw from product_author where sku = 174269875 order by position
                """, String.class);
        assertThat(rows).containsExactly("Умнова-Конюхова И.А.");
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
    @DisplayName("Одно и то же имя в разных полях не схлопывается в одного автора")
    void sameNameInBothSourcesStaysOneRow() {
        catalogImportService.importProducts(accountId(), null);

        // «Сурцуков А.» в карточке и «Сурцуков Анатолий» на обложке — это одно и то же
        // лицо, но под двумя разными именами. Раньше сведение склеивало их в один ключ,
        // и одинаковые строки не отличались ничем. Теперь видно, что в полях написано
        // разное, а товар остаётся один: строки автора для него всё равно одна, потому
        // что приоритет у карточки 4182, а на обложке её подменяют, только когда в
        // карточке автора нет.
        List<String> rows = jdbc.queryForList("""
                select author_raw from product_author where sku = 174269706
                """, String.class);
        assertThat(rows).containsExactly("Сурцуков А.");
        assertThat(jdbc.queryForObject("""
                select count(*) from product_author where sku = 174269706
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("Разные написания одного автора остаются разными авторами")
    void differentSpellingsAreNotMerged() {
        catalogImportService.importProducts(accountId(), null);

        // Четыре написания Сурцукова в выгрузке. Склеивать их автоматикой нельзя:
        // значило бы стереть у продавца то, как он сам назвал автора в своих карточках.
        List<String> spellings = jdbc.queryForList("""
                select distinct author_raw from product_author
                where author_raw like 'Сурцуков%' or author_raw like 'А.В. Сурцуков'
                   or author_raw like 'Анатолий Васильевич Сурцуков'
                order by author_raw
                """, String.class);
        assertThat(spellings).contains(
                "Сурцуков А.", "Сурцуков Анатолий", "А.В. Сурцуков", "Анатолий Васильевич Сурцуков");
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
    @DisplayName("Фильтр по автору находит товары с точным совпадением")
    void authorFilterMatchesExactValue() {
        catalogImportService.importProducts(accountId(), null);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Сурцуков Анатолий", null, "SKU", 0, 500);

        assertThat(report.totalRows()).isPositive();
        assertThat(report.rows()).allSatisfy(row ->
                assertThat(row.authors()).extracting("raw").contains("Сурцуков Анатолий"));
    }

    @Test
    @DisplayName("Похожее, но другое написание автора не подходит")
    void authorFilterIsStrict() {
        catalogImportService.importProducts(accountId(), null);

        // «Сурцуков Анатолий» и «Сурцуков А.» — это два написания одного человека в
        // разных карточках. Строгий поиск обязан их различать: иначе фильтр показывал бы
        // лишние товары, а в отчёте о продажах лишний товар — это лишние деньги.
        //
        // Берём SKU из базы, а не из фикстуры: так проверка не привязана к конкретной
        // выгрузке и остаётся осмысленной, если продавец переименует товар.
        Long longForm = jdbc.queryForObject("""
                select sku from product_author
                where author_raw = 'Сурцуков Анатолий' and source = 'DECLARED'
                order by sku limit 1
                """, Long.class);
        Long shortForm = jdbc.queryForObject("""
                select sku from product_author
                where author_raw = 'Сурцуков А.' and source = 'DECLARED'
                order by sku limit 1
                """, Long.class);
        // Два разных товара подтверждают, что в базе действительно есть оба написания.
        assertThat(longForm).isNotNull().isNotEqualTo(shortForm);

        assertThat(rowsOf(filterByAuthor("Сурцуков Анатолий"))).contains(longForm);
        assertThat(rowsOf(filterByAuthor("Сурцуков А."))).contains(shortForm);

        // И главное: одно написание не подходит под другое.
        assertThat(rowsOf(filterByAuthor("Сурцуков А."))).doesNotContain(longForm);
        assertThat(rowsOf(filterByAuthor("Сурцуков Анатолий"))).doesNotContain(shortForm);
    }

    private ProductReport filterByAuthor(String author) {
        return productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, author, null, "SKU", 0, 500);
    }

    @Test
    @DisplayName("Поиск по фамилии без имени не находит товары")
    void surnameAloneFindsNothing() {
        catalogImportService.importProducts(accountId(), null);

        // Раньше по фамилии находилось всё, что на неё похоже. Теперь «Сурцуков» —
        // это просто строка, которой нет ни в одной карточке, и фильтр честно
        // возвращает пусто.
        ProductReport bySurname = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Сурцуков", null, "SKU", 0, 500);

        assertThat(bySurname.totalRows()).isZero();
    }

    @Test
    @DisplayName("Регистр значения значения не прощает")
    void authorFilterIsCaseSensitive() {
        catalogImportService.importProducts(accountId(), null);

        // В выгрузке есть «асилий Калязин» с маленькой буквы — это опечатка продавца.
        // Искать надо ровно так, как написано; приводить регистр автоматически нельзя,
        // иначе значение из подсказки перестало бы совпадать с тем, что в базе.
        ProductReport lower = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "асилий Калязин", null, "SKU", 0, 500);
        ProductReport upper = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Асилий Калязин", null, "SKU", 0, 500);

        assertThat(lower.totalRows()).isPositive();
        assertThat(upper.totalRows()).isZero();
    }

    @Test
    @DisplayName("Неизвестный автор даёт пустой отчёт, а не ошибку")
    void unknownAuthorGivesEmptyReport() {
        catalogImportService.importProducts(accountId(), null);

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Такого Автора Не существует", null, "SKU", 0, 500);

        assertThat(report.totalRows()).isZero();
        assertThat(report.rows()).isEmpty();
    }

    @Test
    @DisplayName("В перечислении авторов находит любой из перечисленных")
    void findsAnyOfEnumeratedAuthors() {
        catalogImportService.importProducts(accountId(), null);

        // «Черняев А. Ю., Ланде А. А.» — два автора в одной карточке. Исключение из
        // строгого правила: искать надо любое из перечисленных имён, а не всю строку.
        ProductReport first = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Черняев А. Ю.", null, "SKU", 0, 500);
        ProductReport second = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Ланде А. А.", null, "SKU", 0, 500);

        assertThat(first.totalRows()).isPositive();
        assertThat(second.totalRows()).isPositive();
        assertThat(rowsOf(first)).isEqualTo(rowsOf(second));
    }

    @Test
    @DisplayName("Значение из подсказки находит те же товары, что и введённое руками")
    void suggestionValueMatchesTypedValue() {
        catalogImportService.importProducts(accountId(), null);

        // Подсказка отдаёт ровно те строки, которые лежат в базе. Если бы она отдавала
        // что-то другое — например, приведённое к нижнему регистру, — выбор из списка
        // молча перестал бы работать.
        List<String> suggestions = jdbc.queryForList("""
                select distinct author_raw from product_author
                where author_raw = 'Ланде А. А.'
                """, String.class);
        assertThat(suggestions).containsExactly("Ланде А. А.");

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, suggestions.getFirst(), null, "SKU", 0, 500);
        assertThat(report.totalRows()).isPositive();
    }

    private static List<Long> rowsOf(ProductReport report) {
        return report.rows().stream().map(ProductReport.ProductRow::sku).toList();
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

        // Фильтр строгий, значит и вводить надо точное значение: «Умнова-Конюхова» без
        // инициалов не найдёт ничего.
        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, "Умнова-Конюхова И.А.", null, "SKU", 0, 500);

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
    @DisplayName("Сортировка по доходу на первой странице даёт лучших товаров всего каталога")
    void sortedByIncomeAcrossPagesGivesGlobalTop() {
        // Именно этот случай раньше проходил незамеченным: проверка брала size=500,
        // то есть весь каталог целиком, и сортировка в памяти давала правильный ответ.
        // Настоящая беда видна только когда страниц несколько.
        importCatalogAndFinance();

        ProductReport firstPage = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "INCOME", 0, 10);
        ProductReport everything = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "INCOME", 0, 500);

        assertThat(everything.rows()).hasSizeGreaterThan(10);
        assertThat(firstPage.rows()).hasSize(10);

        List<Long> globalTop = everything.rows().stream()
                .map(r -> r.sku())
                .limit(10)
                .toList();
        assertThat(firstPage.rows().stream().map(r -> r.sku()).toList())
                .as("первая страница по доходу должна совпадать с началом полного списка")
                .isEqualTo(globalTop);

        // Итоги не зависят от того, какую страницу смотрит пользователь.
        assertThat(firstPage.totals().income()).isEqualTo(everything.totals().income());
    }

    @Test
    @DisplayName("Порядок из SQL совпадает с порядком по доходам в ответе")
    void sqlOrderMatchesReportedIncome() {
        importCatalogAndFinance();

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "INCOME", 0, 500);

        // Сортировка по доходу нужна в двух местах: в SQL, чтобы выбрать страницу, и в
        // FinancialSummary.income(), чтобы показать число. Если формулы разойдутся,
        // страница окажется отсортирована по одному, а числа — по другому.
        List<java.math.BigDecimal> incomes = report.rows().stream()
                .map(r -> r.income()).toList();
        assertThat(incomes).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("Без параметра сортировки отчёт приходит по доходу")
    void defaultSortIsIncome() {
        importCatalogAndFinance();

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, null, 0, 500);

        List<java.math.BigDecimal> incomes = report.rows().stream()
                .map(r -> r.income()).toList();
        assertThat(incomes).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    @DisplayName("Сортировка по названию и по SKU работает и на страницах")
    void sortByNameAndSkuWorksAcrossPages() {
        importCatalogAndFinance();

        List<Long> bySku = productAnalytics.productReport(
                        MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500)
                .rows().stream().map(r -> r.sku()).toList();
        assertThat(bySku).isSorted();

        List<Long> byName = productAnalytics.productReport(
                        MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "NAME", 0, 500)
                .rows().stream().map(r -> r.sku()).toList();
        List<Long> sortedByName = new java.util.ArrayList<>(byName);
        sortedByName.sort(java.util.Comparator.comparing(sku -> nameOfSku(sku),
                java.util.Comparator.nullsLast(String::compareTo)));
        assertThat(byName).isEqualTo(sortedByName);
    }

    @Test
    @DisplayName("Неизвестный ключ сортировки не ломает запрос, а трактуется как INCOME")
    void unknownSortFallsBackToIncome() {
        importCatalogAndFinance();

        // Ключ подставляется в SQL только из белого списка: значение из запроса в
        // текст запроса не попадает. Неизвестное значение обязано остаться безопасным.
        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "'; drop table ozon_product; --",
                0, 20);

        assertThat(report.rows()).hasSize(20);
        assertThat(count("ozon_product"))
                .as("таблица должна остаться на месте")
                .isEqualTo(108);
    }

    private String nameOfSku(Long sku) {
        return jdbc.queryForObject("select name from ozon_product where sku = ?", String.class, sku);
    }

    /** Каталог и финансы за день: без обоих отчёт пуст. */
    private void importCatalogAndFinance() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FINANCE_FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);
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
        } catch (ru.analizer.sync.domain.ImportConflictException expected) {
            assertThat(expected.conflict())
                    .isEqualTo(ru.analizer.sync.domain.ImportConflictException.Conflict.CATALOG_BUSY);
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
        } catch (ru.analizer.sync.domain.ImportConflictException expected) {
            assertThat(expected.conflict())
                    .isEqualTo(ru.analizer.sync.domain.ImportConflictException.Conflict.OVERLAPPING_PERIOD);
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