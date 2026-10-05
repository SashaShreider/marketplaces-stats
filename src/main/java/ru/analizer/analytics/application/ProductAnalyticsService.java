package ru.analizer.analytics.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.analytics.domain.*;
import ru.analizer.analytics.infrastructure.AnalyticsFactsRepository;
import ru.analizer.analytics.infrastructure.CatalogFacts;
import ru.analizer.analytics.infrastructure.CatalogFacts.ProductAuthorView;
import ru.analizer.catalog.domain.AuthorNormalizer;
import ru.analizer.sync.application.DayStateService;
import ru.analizer.sync.domain.PeriodCoverage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Отчёт по товарам за произвольный период.
 *
 * <p>Считает по сохранённым операциям, как и дневной отчёт: пересчёт из ответа OZON
 * менял бы прошлые цифры при смене правил.
 *
 * <p>Финансовые правила НЕ продублированы, а взяты из {@link FinancialModel} — того же,
 * что считает дневной отчёт. Тогда «доходы, расходы, к выплате» по товару и по дню
 * обязаны совпадать, иначе два отчёта об одном периоде показывали бы разное.
 *
 * <p>Сводка делается в памяти, как в дневном отчёте: так правила не расползаются по
 * запросам. Для длинного периода это много строк, поэтому сортировка по доходу и
 * постраничная выборка применяются после сведения, а не в SQL.
 */
@Service
public class ProductAnalyticsService {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 500;

    private final CatalogFacts catalogFacts;
    private final AnalyticsFactsRepository financeFacts;
    private final DayStateService dayStateService;

    public ProductAnalyticsService(CatalogFacts catalogFacts,
                                   AnalyticsFactsRepository financeFacts,
                                   DayStateService dayStateService) {
        this.catalogFacts = catalogFacts;
        this.financeFacts = financeFacts;
        this.dayStateService = dayStateService;
    }

    /**
     * Отчёт по товарам.
     *
     * @param author что ввёл пользователь в фильтр по автору; сравнивается со сведёнными
     *               ключами, поэтому «Сурцуков А.» находит и «Сурцуков Анатолий»
     * @param sort как упорядочить: {@code INCOME}, {@code NAME}, {@code SKU}
     */
    @Transactional(readOnly = true)
    public ProductReport productReport(String marketplaceCode,
                                  Optional<Long> accountId,
                                  LocalDate from, LocalDate to,
                                  String author, String query,
                                  String sort, int page, int size) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }
        int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        int pageIndex = Math.max(page, 0);

        // Аккаунта может ещё не быть — это «данных нет», а не ошибка. Отчёт обязан
        // сказать об этом прямо, иначе клиент не предложил бы загрузку.
        if (accountId.isEmpty()) {
            return emptyReport(marketplaceCode, from, to, pageIndex, pageSize);
        }
        Long account = accountId.get();

        PeriodCoverage periodCoverage = dayStateService.coverage(account, from, to);
        ReportCoverage coverage = ReportCoverage.from(periodCoverage);

        String authorKey = AuthorNormalizer.toKey(author);
        String authorSurname = AuthorNormalizer.toSurname(author);

        long totalRows = catalogFacts.productsCount(account, authorKey, authorSurname, query);
        int totalPages = (int) Math.max(1, Math.ceil(totalRows / (double) pageSize));
        if (pageIndex >= totalPages) {
            pageIndex = Math.max(0, totalPages - 1);
        }

        // Порядок строк задаёт SQL: постраничная выборка и сортировка должны быть
        // одним шагом, иначе «по доходу» покажет лучшие товары той страницы, на
        // которой они случайно оказались.
        List<CatalogFacts.ProductCatalogRow> pageRows = catalogFacts.productsPage(
                account, authorKey, authorSurname, query, from, to,
                sort, pageIndex * pageSize, pageSize);
        List<Long> skus = pageRows.stream().map(CatalogFacts.ProductCatalogRow::sku).toList();

        Map<Long, List<ProductAuthorView>> authors = catalogFacts.authorsFor(account, skus);
        Map<Long, Integer> quantities = catalogFacts.quantities(account, from, to);
        Map<Long, Integer> accrualCounts = catalogFacts.accrualCounts(account, from, to);

        // Финансы считаем по всем товарам периода, а не по текущей странице: иначе
        // итоги зависели бы от того, какую страницу смотрит пользователь.
        Map<Long, FinancialSummary> bySku = summarizeBySku(account, from, to);
        List<FeeFact> unallocated = catalogFacts.unallocatedFees(account, from, to);
        BigDecimal unallocatedExpenses = unallocated.stream()
                .map(FeeFact::amount)
                .map(v -> v == null ? BigDecimal.ZERO : v)
                .reduce(BigDecimal.ZERO, BigDecimal::add).abs();

        List<ProductReport.ProductRow> rows = new ArrayList<>();
        for (CatalogFacts.ProductCatalogRow row : pageRows) {
            FinancialSummary financial = bySku.getOrDefault(row.sku(),
                    FinancialSummary.empty(from, to));
            rows.add(new ProductReport.ProductRow(
                    row.sku(),
                    row.offerId(),
                    row.name(),
                    row.primaryImage(),
                    row.isbn(),
                    row.typeId(),
                    authors.getOrDefault(row.sku(), List.of()),
                    quantities.getOrDefault(row.sku(), 0),
                    accrualCounts.getOrDefault(row.sku(), 0),
                    financial));
        }

        BigDecimal payout = financeFacts.payoutsByDate(account, from, to).values().stream()
                .map(v -> v == null ? BigDecimal.ZERO : v)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        FinancialSummary all = FinancialSummary.empty(from, to);
        int withSales = 0;
        for (FinancialSummary summary : bySku.values()) {
            all = all.plus(summary);
            if (summary.sales().signum() > 0) {
                withSales++;
            }
        }
        long catalogProducts = catalogFacts.productsCount(account, "", "", "");

        ProductReport.ProductTotals totals = new ProductReport.ProductTotals(
                all.income(), all.expenses(), payout,
                all.sales(), all.returns(), all.partnerProgramme(),
                all.commission(), all.logistics(), all.otherExpenses(),
                (int) catalogProducts, withSales);

        return new ProductReport(marketplaceCode, from, to,
                DailyAnalyticsService.statusOf(periodCoverage), coverage,
                new ProductReport.CatalogInfo(catalogProducts,
                        catalogFacts.lastCatalogSync(account), catalogProducts > 0),
                totals, rows, pageIndex, pageSize, totalRows, totalPages,
                unallocatedExpenses,
                catalogFacts.skusMissingFromCatalog(account, from, to));
    }

    /**
     * Сводит операции периода по SKU через общую финансовую модель.
     *
     * <p>Продажи и расходы считаются только по SKU, присутствующим в операциях.
     * NON_ITEM и CONTAINER сюда не попадают: у них товара нет.
     */
    private Map<Long, FinancialSummary> summarizeBySku(Long accountId, LocalDate from, LocalDate to) {
        Map<Long, List<ProductFact>> productsBySku = new LinkedHashMap<>();
        for (ProductFact fact : financeFacts.products(accountId, from, to)) {
            productsBySku.computeIfAbsent(fact.sku(), k -> new ArrayList<>()).add(fact);
        }
        Map<Long, List<FeeFact>> feesBySku = new LinkedHashMap<>();
        List<FeeFact> fees = new ArrayList<>();
        fees.addAll(financeFacts.deliveryFees(accountId, from, to));
        fees.addAll(financeFacts.itemFees(accountId, from, to));
        for (FeeFact fee : fees) {
            if (fee.sku() != null) {
                feesBySku.computeIfAbsent(fee.sku(), k -> new ArrayList<>()).add(fee);
            }
        }

        Map<Long, FinancialSummary> result = new LinkedHashMap<>();
        for (Long sku : productsBySku.keySet()) {
            result.put(sku, FinancialModel.summarize(from, to,
                    productsBySku.getOrDefault(sku, List.of()),
                    feesBySku.getOrDefault(sku, List.of()),
                    Map.of()));
        }
        // Расходы могут прийти по SKU, у которого в этом периоде не было продаж:
        // например, штраф за непроданный остаток. Такой товар тоже показываем.
        for (Long sku : feesBySku.keySet()) {
            result.computeIfAbsent(sku, k -> FinancialModel.summarize(from, to,
                    productsBySku.getOrDefault(k, List.of()), feesBySku.get(k), Map.of()));
        }
        return result;
    }

    private ProductReport emptyReport(String marketplaceCode, LocalDate from, LocalDate to,
                                      int pageIndex, int pageSize) {
        PeriodCoverage nothing = PeriodCoverage.empty(from, to);
        FinancialSummary zero = FinancialSummary.empty(from, to);
        return new ProductReport(marketplaceCode, from, to,
                DailyAnalyticsService.statusOf(nothing), ReportCoverage.from(nothing),
                new ProductReport.CatalogInfo(0, null, false),
                new ProductReport.ProductTotals(
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, 0, 0),
                List.of(), pageIndex, pageSize, 0, 1, BigDecimal.ZERO, List.of());
    }

    /**
     * Проверка, что ничего не потерялось при разбивке по SKU.
     *
     * <p>Сравниваются ЗНАКОВЫЕ суммы, а не модули: {@code abs()} по каждому товару не
     * складывается. Если у одного товара логистика −100, а у другого +50, то по модулю
     * получится 150, а по факту 50 — и такая проверка всегда давала бы ложное
     * расхождение в тысячи рублей.
     */
    @Transactional(readOnly = true)
    public BigDecimal expensesReconciliationDiff(Long accountId, LocalDate from, LocalDate to) {
        Map<Long, FinancialSummary> bySku = summarizeBySku(accountId, from, to);
        BigDecimal productSide = bySku.values().stream()
                .map(s -> n(s.commission()).add(n(s.logistics())).add(n(s.otherExpenses())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal nonItem = BigDecimal.ZERO;
        BigDecimal container = BigDecimal.ZERO;
        for (FeeFact fee : catalogFacts.unallocatedFees(accountId, from, to)) {
            if (fee.kind() == FeeFact.FeeKind.CONTAINER) {
                container = container.add(n(fee.amount()));
            } else {
                nonItem = nonItem.add(n(fee.amount()));
            }
        }
        BigDecimal unallocatedSide = nonItem.add(container);

        BigDecimal daily = n(financeFacts.commission(accountId, from, to))
                .add(n(financeFacts.logistics(accountId, from, to)))
                .add(n(financeFacts.itemFeesTotal(accountId, from, to)))
                .add(nonItem)
                .add(container);

        return productSide.add(unallocatedSide).subtract(daily);
    }

    private static BigDecimal n(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
