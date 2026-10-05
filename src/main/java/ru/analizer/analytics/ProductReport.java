package ru.analizer.analytics;

import java.math.BigDecimal;
import java.util.List;

/**
 * Отчёт по товарам за период.
 *
 * <p>Строки — все товары каталога, а не только проданные. Здесь это принципиально и
 * обратно дневному отчёту:
 * <ul>
 *   <li>в дневном отчёте ноль за незагруженный день — это ложь, поэтому день помечается
 *       как незагруженный;</li>
 *   <li>в отчёте по товарам ноль у товара, который есть в каталоге, — правда: товар
 *       существует, продаж за период не было. Молчать о нём означало бы скрыть товары,
 *       которые перестали продаваться.</li>
 * </ul>
 *
 * <p>Но если период загружен не полностью, честность всё равно нужна: отчёт помечается
 * {@link ReportStatus}, а {@code coverage} перечисляет недостающие дни. Иначе нули по
 * товарам оказывались бы ложью на уровне периода.
 *
 * @param unallocatedExpenses расходы, у которых OZON не указал товар (NON_ITEM,
 *                            CONTAINER). Их нельзя распределить между товарами, не
 *                            придумав правило, поэтому они показаны отдельно: сумма
 *                            расходов по товарам намеренно меньше дневной
 * @param skusMissingFromCatalog SKU из начислений, которых нет в каталоге: у них нет ни
 *                              названия, ни автора, показать их в строках нечем
 */
public record ProductReport(
        String marketplace,
        java.time.LocalDate dateFrom,
        java.time.LocalDate dateTo,
        ReportStatus status,
        DataCoverage coverage,
        CatalogInfo catalog,
        ProductTotals totals,
        List<ProductRow> rows,
        int page,
        int size,
        long totalRows,
        int totalPages,
        BigDecimal unallocatedExpenses,
        List<Long> skusMissingFromCatalog
) {

    /** Данные каталога: сколько товаров и когда он последний раз обновлялся. */
    public record CatalogInfo(long products, java.time.Instant lastSyncedAt, boolean loaded) {
    }

    /** Итоги периода. Считаются по всем товарам, а не по текущей странице. */
    public record ProductTotals(
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal payout,
            BigDecimal sales,
            BigDecimal returns,
            BigDecimal partnerProgramme,
            BigDecimal commission,
            BigDecimal logistics,
            BigDecimal itemExpenses,
            int productsInCatalog,
            int productsWithSales
    ) {
    }

    /** Товар в отчёте. */
    public record ProductRow(
            long sku,
            String offerId,
            String name,
            String primaryImage,
            String isbn,
            Long typeId,
            List<CatalogFacts.ProductAuthorView> authors,
            int quantity,
            int accrualCount,
            FinancialSummary financial
    ) {

        /** Доход товара. */
        @com.fasterxml.jackson.annotation.JsonProperty
        public BigDecimal income() {
            return financial.income();
        }

        /** Расходы товара положительным числом. */
        @com.fasterxml.jackson.annotation.JsonProperty
        public BigDecimal expenses() {
            return financial.expenses();
        }
    }
}