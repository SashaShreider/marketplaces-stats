package ru.analizer.analytics.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.analytics.domain.*;
import ru.analizer.analytics.infrastructure.AnalyticsFactsRepository;
import ru.analizer.analytics.infrastructure.CatalogFacts;
import ru.analizer.analytics.infrastructure.CatalogFacts.ProductAuthorView;
import ru.analizer.analytics.infrastructure.filter.ProductAttributeFilter;
import ru.analizer.analytics.infrastructure.filter.ProductAuthorFilter;
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
     * @param author что ввёл пользователь в фильтр по автору; сравнивается точным
     *               совпадением с тем, как автор назван в карточке товара, поэтому
     *               «Сурцуков А.» не найдёт «Сурцуков Анатолий»
     * @param sort как упорядочить строки: {@code INCOME}, {@code NAME}, {@code SKU},
     *             {@code SOLD}, {@code EXPENSES} или {@code GROSS}
     * @param order направление порядка: {@code asc} или {@code desc}; пустое — как
     *              привычнее для выбранного критерия
     * @param onlyWithSales оставить только товары, у которых были продажи
     */
    @Transactional(readOnly = true)
    public ProductReport productReport(String marketplaceCode,
                                  Optional<Long> accountId,
                                  LocalDate from, LocalDate to,
                                  String author, String query,
                                  String sort, String order, boolean onlyWithSales,
                                  int page, int size) {
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

// Фильтры собираются в одном месте: добавить признак — значит добавить сюда
        // ещё одну реализацию ProductAttributeFilter, а не править SQL в выборках.
        List<ProductAttributeFilter> filters =
                List.of(ProductAuthorFilter.byValue(author));

        long totalRows = catalogFacts.productsCount(account, filters, query, from, to, onlyWithSales);
        int totalPages = (int) Math.max(1, Math.ceil(totalRows / (double) pageSize));
        if (pageIndex >= totalPages) {
            pageIndex = Math.max(0, totalPages - 1);
        }

        // Порядок строк задаёт SQL: постраничная выборка и сортировка должны быть
        // одним шагом, иначе «по доходу» покажет лучшие товары той страницы, на
        // которой они случайно оказались.
        List<CatalogFacts.ProductCatalogRow> pageRows = catalogFacts.productsPage(
                account, filters, query, from, to,
                sort, order, onlyWithSales, pageIndex * pageSize, pageSize);
        List<Long> skus = pageRows.stream().map(CatalogFacts.ProductCatalogRow::sku).toList();

        Map<Long, List<ProductAuthorView>> authors = catalogFacts.authorsFor(account, skus);
        Map<Long, CatalogFacts.Quantities> quantities = catalogFacts.quantities(account, from, to);
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
            // Количества берём из готовой карты, а не из financial: в сводке они уже
            // посчитаны по всем SKU периода, и складывать их здесь значило бы
            // получить то же самое вторым способом.
            CatalogFacts.Quantities quantity = quantities.getOrDefault(row.sku(),
                    new CatalogFacts.Quantities(0, 0));
            rows.add(new ProductReport.ProductRow(
                    row.sku(),
                    row.offerId(),
                    row.name(),
                    row.primaryImage(),
                    row.isbn(),
                    row.typeId(),
                    authors.getOrDefault(row.sku(), List.of()),
                    quantity.sold(),
                    quantity.returned(),
                    accrualCounts.getOrDefault(row.sku(), 0),
                    financial, row.archived()));
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
        // Все товары аккаунта, без фильтров: это состояние каталога, а не результат
        // отбора. Отфильтрованных строк здесь было бы меньше, и клиент принял бы
        // число за размер каталога.
        long catalogProducts = catalogFacts.productsCount(
                account, List.of(), null, from, to, false);

        // Второе число для клиента: итоги по отбору, а не по каталогу. Считаем один раз
        // и сразу по всем SKU отбора, а не по странице, — иначе «Итого» прыгал бы
        // при перелистывании. Без фильтра отбор это весь каталог, и тогда числа
        // совпадают с totals, а не расходятся на пустом месте.
        Set<Long> selection = filters.isEmpty() && (query == null || query.isBlank()) && !onlyWithSales
                ? null
                : new HashSet<>(catalogFacts.selectionSkus(
                        account, filters, query, from, to, onlyWithSales));

        FinancialSummary selected = FinancialSummary.empty(from, to);
        for (Map.Entry<Long, FinancialSummary> entry : bySku.entrySet()) {
            if (selection != null && !selection.contains(entry.getKey())) {
                continue;
            }
            selected = selected.plus(entry.getValue());
        }

        ProductReport.ProductTotals totals = new ProductReport.ProductTotals(
                all.income(), all.expenses(), payout,
                all.sales(), all.returns(), all.partnerProgramme(),
                all.commission(), all.logistics(), all.otherExpenses(),
                all.soldQuantity(), all.returnedQuantity(),
                (int) catalogProducts, withSales);

        ProductReport.SelectionTotals selectionTotals = new ProductReport.SelectionTotals(
                selected.soldQuantity(), selected.income(), selected.expenses());

        return new ProductReport(marketplaceCode, from, to,
                DailyAnalyticsService.statusOf(periodCoverage), coverage,
                new ProductReport.CatalogInfo(catalogProducts,
                        catalogFacts.lastCatalogSync(account), catalogProducts > 0),
                totals, selectionTotals, rows, pageIndex, pageSize, totalRows, totalPages,
                unallocatedExpenses,
                catalogFacts.skusMissingFromCatalog(account, from, to));
    }

      /**
       * Продажи по дням периода, с ограничением на автора или на конкретный SKU.
       *
       * <p>Нужен для графика под таблицей товаров: в самом отчёте строки это товары,
       * а не дни, и без отдельного запроса динамику пришлось бы собирать на клиенте из
       * всех товаров разом — то есть скачивать весь отчёт только ради рисунка.
       *
       * <p>Фильтр по SKU и по автору несовместимы: это два разных отбора, и применять
       * оба сразу означало бы искать товар одновременно у двух продавцов. Задача одна,
       * поэтому при заданном SKU автор игнорируется — так решает пользователь,
       * вводя номер конкретного товара.
       *
       * <p>Кроме количества день несёт деньги: графику они не нужны, но таблица под
       * ним показывает экономику конкретных товаров — доход, комиссию, логистику,
       * продвижение, прочие расходы и выручку. Считаются они так же, как в отчёте по
       * товарам: только по начислениям с привязкой к SKU. Списания без товара не
       * распределяются между книгами, поэтому в строки не входят, а их сумма за
       * период едет отдельным полем отчёта.
       *
       * @param author автор ровно в том написании, в каком указан в карточке товара
       * @param sku    конкретный SKU; {@code null} — без ограничения
       */
      public SalesDynamicsReport salesDynamics(String marketplaceCode,
                                                Optional<Long> accountId,
                                                LocalDate from, LocalDate to,
                                                String author, Long sku) {
          if (from == null || to == null) {
              throw new IllegalArgumentException("dateFrom и dateTo обязательны");
          }
          if (to.isBefore(from)) {
              throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
          }
          // Аккаунта нет — это «данных нет», а не ошибка: пустой график с честным
          // покрытием даст клиенту показать предложение загрузить данные.
          if (accountId.isEmpty()) {
              return new SalesDynamicsReport(from, to, ReportStatus.NOT_LOADED,
                      ReportCoverage.from(PeriodCoverage.empty(from, to)), List.of(),
                      labelOf(author, sku), BigDecimal.ZERO);
          }
          Long account = accountId.get();

          PeriodCoverage periodCoverage = dayStateService.coverage(account, from, to);
          List<CatalogFacts.SalesDay> quantities = catalogFacts.salesDynamics(
                  account,
                  List.of(ProductAuthorFilter.byValue(sku == null ? author : null)),
                  sku, from, to);
          Map<LocalDate, DayMoney> money = moneyByDay(marketplaceCode, account, author, sku, from, to);

          // Дни объединяются, а не берутся из одного источника: начисление по товару
          // может прийти в день без продаж (например, продвижение или хранение), и
          // пропустить такой день значило бы потерять расход. Обратное тоже верно —
          // продажа без начислений обязана остаться в графике.
          Map<LocalDate, SalesDynamicsReport.SalesDay> byDate = new TreeMap<>();
          for (CatalogFacts.SalesDay day : quantities) {
              byDate.put(day.date(), reportDay(day.date(), day.sold(), day.returned(),
                      money.get(day.date())));
          }
          for (Map.Entry<LocalDate, DayMoney> entry : money.entrySet()) {
              byDate.putIfAbsent(entry.getKey(), reportDay(entry.getKey(), 0, 0, entry.getValue()));
          }

          // Расходы без SKU в дни не входят, но и исчезать из ответа не должны: иначе
          // расходы динамики не сойдутся с дневным отчётом и непонятно будет, где
          // ошибка. Сумма та же, что показывает отчёт по товарам.
          BigDecimal unallocated = catalogFacts.unallocatedFees(account, from, to).stream()
                  .map(FeeFact::amount)
                  .map(v -> v == null ? BigDecimal.ZERO : v)
                  .reduce(BigDecimal.ZERO, BigDecimal::add).abs();

          return new SalesDynamicsReport(from, to,
                  DailyAnalyticsService.statusOf(periodCoverage),
                  ReportCoverage.from(periodCoverage),
                  List.copyOf(byDate.values()),
                  labelOf(author, sku),
                  unallocated);
      }

      /**
       * Деньги по дням периода для того же отбора, что и количество.
       *
       * <p>Свод считается той же {@link FinancialModel}, что и отчёт по товарам, и по
       * тому же набору начислений: только с привязкой к SKU. Поэтому выручка динамики
       * сходится с выручкой в таблице товаров, а вопрос у обеих таблиц один — сколько
       * заработала книга.
       *
       * <p>Списания без SKU (NON_ITEM, CONTAINER) сюда не попадают намеренно. Это
       * расходы магазина, а не книги: распределить их между товарами можно только
       * придумав правило, и тогда «выручка за книгу» стала бы меньше заработка на самой
       * книге. Их сумма приходит отдельным полем отчёта.
       */
      private Map<LocalDate, DayMoney> moneyByDay(String marketplaceCode, Long account,
                                                  String author, Long sku,
                                                  LocalDate from, LocalDate to) {
          Set<Long> selection = selectionFor(account, author, sku, from, to);

          Map<Integer, FinancialModel.TypeLabels> labels =
                  financeFacts.accrualTypeLabels(marketplaceCode);
          Set<Integer> promotionTypes = FinancialModel.promotionTypeIds(labels);

          Map<LocalDate, List<ProductFact>> productsByDay = new LinkedHashMap<>();
          for (ProductFact fact : financeFacts.products(account, from, to)) {
              if (selection != null && !selection.contains(fact.sku())) {
                  continue;
              }
              productsByDay.computeIfAbsent(fact.date(), k -> new ArrayList<>()).add(fact);
          }

          // Только начисления с привязкой к SKU: доставка и позиции товаров. Списания
          // без товара — расход магазина, и в экономику книги они не входят.
          List<FeeFact> fees = new ArrayList<>();
          fees.addAll(financeFacts.deliveryFees(account, from, to));
          fees.addAll(financeFacts.itemFees(account, from, to));
          Map<LocalDate, List<FeeFact>> feesByDay = new LinkedHashMap<>();
          for (FeeFact fee : fees) {
              if (fee.sku() == null) {
                  continue;
              }
              if (selection != null && !selection.contains(fee.sku())) {
                  continue;
              }
              feesByDay.computeIfAbsent(fee.date(), k -> new ArrayList<>()).add(fee);
          }

          Set<LocalDate> dates = new TreeSet<>();
          dates.addAll(productsByDay.keySet());
          dates.addAll(feesByDay.keySet());

          Map<LocalDate, DayMoney> result = new LinkedHashMap<>();
          for (LocalDate date : dates) {
              List<FeeFact> dayFees = feesByDay.getOrDefault(date, List.of());
              FinancialSummary summary = FinancialModel.summarize(
                      date, date,
                      productsByDay.getOrDefault(date, List.of()),
                      dayFees,
                      Map.of());
              BigDecimal promotion = FinancialModel.promotionExpenses(dayFees, promotionTypes);
              result.put(date, new DayMoney(
                      summary.income(),
                      summary.commission(),
                      summary.logistics(),
                      promotion,
                      // «Прочие» без продвижения: иначе оно попало бы и в свою
                      // колонку, и в «другие расходы», а сумма расходов выросла бы
                      // на его же величину.
                      summary.otherExpenses().subtract(promotion),
                      summary.income().subtract(summary.expenses())));
          }
          return result;
      }

      /** SKU отбора или {@code null}, если ограничения нет. */
      private Set<Long> selectionFor(Long account, String author, Long sku,
                                     LocalDate from, LocalDate to) {
          if (sku != null) {
              return Set.of(sku);
          }
          if (author == null || author.isBlank()) {
              return null;
          }
          return new HashSet<>(catalogFacts.selectionSkus(account,
                  List.of(ProductAuthorFilter.byValue(author)), null, from, to, false));
      }

      private static SalesDynamicsReport.SalesDay reportDay(LocalDate date, int sold, int returned,
                                                            DayMoney money) {
          DayMoney m = money == null ? DayMoney.ZERO : money;
          return new SalesDynamicsReport.SalesDay(date, sold, returned,
                  m.income(), m.commission(), m.logistics(), m.promotion(),
                  m.otherExpenses(), m.revenue());
      }

      /** Деньги одного дня — до сборки в ответ. */
      private record DayMoney(BigDecimal income, BigDecimal commission, BigDecimal logistics,
                              BigDecimal promotion, BigDecimal otherExpenses, BigDecimal revenue) {

          private static final DayMoney ZERO = new DayMoney(BigDecimal.ZERO, BigDecimal.ZERO,
                  BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
      }

      /**
       * Подпись отбора над графиком.
       *
       * <p>Нужна, чтобы было видно, что именно показано. Без неё график выглядел бы
       * как общий, даже когда выбран один товар из сотни, и результат читался бы как
       * данные по всему магазину.
       */
      private static String labelOf(String author, Long sku) {
          if (sku != null) {
              return "SKU " + sku;
          }
          return author == null || author.isBlank() ? "Все товары" : author;
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
                        BigDecimal.ZERO, 0, 0, 0, 0),
                new ProductReport.SelectionTotals(0, BigDecimal.ZERO, BigDecimal.ZERO),
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
