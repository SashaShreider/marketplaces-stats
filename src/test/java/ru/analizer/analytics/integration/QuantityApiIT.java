package ru.analizer.analytics.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import ru.analizer.analytics.domain.ProductReport;
import ru.analizer.support.AbstractPostgresIntegrationTest;
import ru.analizer.support.CatalogAdapterConfig;
import ru.analizer.support.FixtureAdapterConfig;
import ru.analizer.support.FixtureAdapters;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Количество продаж и возвратов, посчитанное SQL.
 *
 * <p>Отдельно от доменной модели, потому что количество приходит сюда двумя разными
 * путями, и они могут разойтись незаметно:
 * <ul>
 *   <li>в {@code FinancialModel} количество считается при свёртке уже выбранных строк;</li>
 *   <li>в отчёте по товарам — агрегатом по SKU в самом SQL.</li>
 * </ul>
 * Проверка только первого пути оставила бы второй без присмотра, и в отчёте по товарам
 * количество разошлось бы с дневным.
 *
 * <p>Фикстура настоящая, за 2026-04-11: продажа двух единиц, удержание без комиссии и
 * возврат. Товары в каталоге настоящие, но нужны только их идентификаторы, чтобы строки
 * отчёта существовали: без каталога отчёт по товарам пуст, и проверять нечего.
 */
@Import({CatalogAdapterConfig.class, FixtureAdapterConfig.class})
class QuantityApiIT extends AbstractPostgresIntegrationTest {

    private static final String MARKETPLACE = "OZON";

    /** День, под который собран ответ с тремя разными случаями. */
    private static final LocalDate DAY = LocalDate.of(2026, 4, 11);
    private static final String FIXTURE = "fixtures/accruals-quantity-2026-04-11.json";

    /** SKU проданной позиции и удержания: удержание пришло по тому же товару. */
    private static final long SOLD_SKU = 1720912969L;
    /** SKU возврата: у него продаж не было вовсе. */
    private static final long RETURNED_SKU = 1973850522L;

    private void importFixture() {
        catalogImportService.importProducts(accountId(), null);
        FixtureAdapters.FIXTURES.put(DAY, FIXTURE);
        accrualImportService.importAccruals(accountId(), DAY, DAY);
    }

    private ProductReport.ProductRow rowOf(ProductReport report, long sku) {
        return report.rows().stream()
                .filter(row -> row.sku() == sku)
                .findFirst()
                .orElseThrow(() -> new AssertionError("в отчёте нет товара " + sku));
    }

    @Test
    @DisplayName("Удержание без комиссии не попадает в количество проданных")
    void rowsWithoutCommissionAreNotCountedAsSold() {
        importFixture();

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        ProductReport.ProductRow row = rowOf(report, SOLD_SKU);
        // Две единицы проданы, и сверху ещё одна строка — удержание за доставку.
        // Наивное суммирование quantity дало бы здесь 3.
        assertThat(row.soldQuantity())
                .as("удержание не является продажей")
                .isEqualTo(2);
        assertThat(row.returnedQuantity()).isEqualTo(0);
    }

    @Test
    @DisplayName("Возврат попадает в своё поле, а не в проданные")
    void returnsAreCountedSeparately() {
        importFixture();

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        ProductReport.ProductRow row = rowOf(report, RETURNED_SKU);
        assertThat(row.soldQuantity()).isZero();
        assertThat(row.returnedQuantity())
                .as("quantity у возврата положителен")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Итоги периода совпадают с суммой по товарам")
    void totalsMatchTheSumOverProducts() {
        importFixture();

        ProductReport report = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        int soldByRows = report.rows().stream().mapToInt(ProductReport.ProductRow::soldQuantity).sum();
        int returnedByRows = report.rows().stream()
                .mapToInt(ProductReport.ProductRow::returnedQuantity).sum();

        assertThat(report.totals().soldQuantity()).isEqualTo(soldByRows);
        assertThat(report.totals().returnedQuantity()).isEqualTo(returnedByRows);
        assertThat(report.totals().soldQuantity())
                .as("три единицы: две проданы, одна возвращена")
                .isEqualTo(2);
        assertThat(report.totals().returnedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("Дневной отчёт и отчёт по товарам дают одинаковое количество")
    void dailyReportAgreesWithProductReport() {
        importFixture();

        var daily = analytics.dailyReport(MARKETPLACE, accountIdOpt(), DAY, DAY);
        ProductReport products = productAnalytics.productReport(
                MARKETPLACE, accountIdOpt(), DAY, DAY, null, null, "SKU", 0, 500);

        // Два пути считают одно и то же по-разному: в дневном отчёте свёртка строк,
        // в товарном — агрегат SQL. Расхождение означало бы, что одно из двух врёт.
        assertThat(daily.total().soldQuantity()).isEqualTo(products.totals().soldQuantity());
        assertThat(daily.total().returnedQuantity()).isEqualTo(products.totals().returnedQuantity());

        ProductReport.ProductRow first = products.rows().stream()
                .filter(row -> row.soldQuantity() > 0)
                .findFirst()
                .orElseThrow();
        assertThat(first.soldQuantity()).isEqualTo(2);
    }
}
