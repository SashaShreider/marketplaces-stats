package ru.analizer.analytics.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.analytics.domain.*;
import ru.analizer.analytics.infrastructure.AnalyticsFactsRepository;
import ru.analizer.analytics.infrastructure.CatalogFacts;
import ru.analizer.sync.application.DayStateService;
import ru.analizer.sync.domain.PeriodCoverage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Ежедневная аналитика за произвольный период.
 *
 * <p>Считает по сохранённым операциям, а не по ответу OZON: отчёт должен оставаться
 * прежним при смене правил расчёта.
 *
 * <p>Отчёт всегда отдаёт состояние данных ({@link ReportStatus}, {@link ReportCoverage}).
 * Без этого нули за незагруженный день неотличимы от нулей за день без начислений,
 * и пользователь решил бы, что денег не было.
 */
@Service
public class DailyAnalyticsService {

    private final AnalyticsFactsRepository facts;
    private final DayStateService dayStateService;
    private final CatalogFacts catalogFacts;

    public DailyAnalyticsService(AnalyticsFactsRepository facts,
                                 DayStateService dayStateService,
                                 CatalogFacts catalogFacts) {
        this.facts = facts;
        this.dayStateService = dayStateService;
        this.catalogFacts = catalogFacts;
    }

    /**
     * Отчёт за весь магазин.
     *
     * <p>Оставлен ради читателей, которым отбор не нужен: {@link ReportScope#all()}
     * выражает это явно, но в вызовах без фильтра он только шумел бы.
     */
    @Transactional(readOnly = true)
    public DailyReport dailyReport(String marketplaceCode, Optional<Long> accountId,
                                    LocalDate from, LocalDate to) {
        return dailyReport(marketplaceCode, accountId, from, to, ReportScope.all());
    }

    /**
     * Отчёт за период в пределах отбора.
     *
     * @param accountId аккаунт, по которому считаем; пусто — маркетплейс не подключён.
     *                  Аккаунт передаётся явно, а не ищется здесь: поиск идёт через
     *                  сессию, а значит работает только в потоке запроса. Из фоновой
     *                  задачи или теста сессии нет, и метод упал бы с отказом доступа.
     * @param scope     отбор по автору или по списку товаров; {@code null} — весь магазин
     */
    @Transactional(readOnly = true)
    public DailyReport dailyReport(String marketplaceCode, Optional<Long> accountId,
                                    LocalDate from, LocalDate to, ReportScope scope) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }
        ReportScope reportScope = scope == null ? ReportScope.all() : scope;
        // Аккаунта может ещё не быть — это не сбой, а «данных нет». Отчёт обязан сказать
        // об этом прямо (NOT_LOADED), иначе фронтенд не сможет предложить загрузку.
        if (accountId.isEmpty()) {
            PeriodCoverage nothing = PeriodCoverage.empty(from, to);
            return emptyReport(marketplaceCode, from, to, ReportCoverage.from(nothing), nothing,
                    reportScope);
        }
        Long account = accountId.get();

        PeriodCoverage periodCoverage = dayStateService.coverage(account, from, to);
        ReportCoverage coverage = ReportCoverage.from(periodCoverage);

        if (periodCoverage.isEmpty()) {
            return emptyReport(marketplaceCode, from, to, coverage, periodCoverage, reportScope);
        }

        Map<Integer, FinancialModel.TypeLabels> typeLabels = facts.accrualTypeLabels(marketplaceCode);

        List<ProductFact> products = facts.products(account, from, to);
        List<FeeFact> fees = new ArrayList<>();
        fees.addAll(facts.deliveryFees(account, from, to));
        fees.addAll(facts.itemFees(account, from, to));
        fees.addAll(facts.nonItemFees(account, from, to));
        fees.addAll(facts.containerFees(account, from, to));

        // Отбор сужает списки фактов до расчёта, а не после: тогда все показатели —
        // дни, итог, раскладка расходов — считаются из одного и того же набора, и
        // разойтись между собой не могут.
        Set<Long> authorSkus = reportScope.author() == null ? null
                : catalogFacts.skusByAuthor(account, reportScope.author());
        if (reportScope.scoped()) {
            products = products.stream()
                    .filter(p -> reportScope.accepts(p.sku(), authorSkus))
                    .toList();
            fees = fees.stream()
                    .filter(f -> reportScope.acceptsFee(f, authorSkus))
                    .toList();
        }

        // Расходы без товара не распределяются: их сумма показывается отдельно, чтобы
        // усечённые цифры не выглядели полными.
        BigDecimal unallocated = reportScope.scoped()
                ? facts.unallocatedTotal(account, from, to)
                : BigDecimal.ZERO;

        // Выплату по части товаров взять негде: OZON отдаёт total_amount по всему
        // магазину. Для отбора выплата не заполняется вовсе — ноль означал бы
        // «выплаты не было».
        Map<LocalDate, BigDecimal> payouts = reportScope.scoped()
                ? Map.of()
                : facts.payoutsByDate(account, from, to);

        // Дни периода в порядке возрастания: порядок строк в отчёте совпадает с календарём,
        // а не с тем, в каком порядке пришли начисления.
        Map<LocalDate, BigDecimal> payoutByDate = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            payoutByDate.put(date, BigDecimal.ZERO);
        }
        payouts.forEach((date, amount) ->
                payoutByDate.put(date, amount == null ? BigDecimal.ZERO : amount));

        // Товары и расходы приходят одним списком за весь период, поэтому на каждый
        // день отбираем свою часть.
        Map<LocalDate, FinancialSummary> byDate = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : payoutByDate.entrySet()) {
            LocalDate date = entry.getKey();
            // Пустая карта выплат — это «выплата неизвестна», а не «выплата ноль»:
            // при отборе по товарам долю магазина в total_amount взять неоткуда.
            Map<LocalDate, BigDecimal> dayPayouts = reportScope.scoped()
                    ? null
                    : Map.of(date, entry.getValue());
            FinancialSummary day = FinancialModel.summarize(date, date,
                    filterByDate(products, date), filterFeesByDate(fees, date), dayPayouts);
            byDate.put(date, reportScope.scoped() ? day.withoutPayout() : day);
        }

        List<DailyRow> rows = new ArrayList<>();
        for (FinancialSummary day : byDate.values()) {
            List<FeeFact> dayFees = filterFeesByDate(fees, day.dateFrom());
            rows.add(DailyRow.of(day,
                    FinancialModel.expensesByType(dayFees, typeLabels),
                    FinancialModel.expenseBreakdown(dayFees, day.commission(), typeLabels)));
        }

        FinancialSummary total = FinancialSummary.empty(from, to);
        for (FinancialSummary day : byDate.values()) {
            total = total.plus(day);
        }
        if (reportScope.scoped()) {
            total = total.withoutPayout();
        }

        return new DailyReport(marketplaceCode, from, to, statusOf(periodCoverage), coverage, rows,
                total.income(), total.expenses(), total.payout(), total, reconciles(byDate),
                FinancialModel.expenseBreakdown(fees, total.commission(), typeLabels),
                reportScope.label(), reportScope.scoped(), unallocated);
    }

    private DailyReport emptyReport(String marketplaceCode, LocalDate from, LocalDate to,
                                    ReportCoverage coverage, PeriodCoverage periodCoverage,
                                    ReportScope scope) {
        List<DailyRow> rows = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            rows.add(DailyRow.of(FinancialSummary.empty(date, date),
                    new FinancialSummary.ExpenseByType(List.of()),
                    ExpenseBreakdown.empty()));
        }
        // Показывать нечего. В отчёте по всему магазину это честный ноль и сходится
        // с нулём; в отчёте по части товаров выплаты по-прежнему нет — её не из чего
        // выделить, — поэтому там поля остаются неизвестными.
        FinancialSummary zero = scope.scoped()
                ? FinancialSummary.empty(from, to).withoutPayout()
                : FinancialSummary.empty(from, to);
        Boolean reconciled = scope.scoped() ? null : Boolean.TRUE;
        return new DailyReport(marketplaceCode, from, to, statusOf(periodCoverage), coverage, rows,
                BigDecimal.ZERO, BigDecimal.ZERO, zero.payout(), zero, reconciled,
                ExpenseBreakdown.empty(), scope.label(), scope.scoped(), BigDecimal.ZERO);
    }

    /**
     * Итоговый статус отчёта по покрытию периода.
     *
     * <p>Порядок важен: наличие ошибок важнее полноты, а полнота — важнее успеха.
     * Пользователь должен видеть, почему цифры неполны, а не просто «0».
     */
    public static ReportStatus statusOf(PeriodCoverage coverage) {
        if (coverage.failedDays() > 0) {
            return ReportStatus.HAS_ERRORS;
        }
        if (coverage.isEmpty()) {
            return ReportStatus.NOT_LOADED;
        }
        if (!coverage.complete()) {
            return ReportStatus.PARTIAL;
        }
        return ReportStatus.READY;
    }

    /**
     * Проверка, что по каждому дню доходы минус расходы равны данным OZON.
     * Ненулевое расхождение означало бы, что мы не учли какой-то вид начисления —
     * такой день помечается, а не замалчивается.
     *
     * <p>{@code null}, если сверять не с чем: при отборе по товарам выплата
     * неизвестна, и «не сходится» было бы таким же вымыслом, как «сходится».
     */
    private Boolean reconciles(Map<LocalDate, FinancialSummary> byDate) {
        Boolean result = Boolean.TRUE;
        for (FinancialSummary day : byDate.values()) {
            Boolean dayReconciles = day.reconciles();
            if (dayReconciles == null) {
                return null;
            }
            if (!dayReconciles) {
                result = Boolean.FALSE;
            }
        }
        return result;
    }

    private List<ProductFact> filterByDate(List<ProductFact> products, LocalDate date) {
        return products.stream().filter(p -> p.date().equals(date)).toList();
    }

    private List<FeeFact> filterFeesByDate(List<FeeFact> fees, LocalDate date) {
        return fees.stream().filter(f -> f.date().equals(date)).toList();
    }



    private static BigDecimal n(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}