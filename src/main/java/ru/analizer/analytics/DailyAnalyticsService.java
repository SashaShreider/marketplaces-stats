package ru.analizer.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.persistence.entity.Marketplace;

import ru.analizer.sync.PeriodCoverage;
import ru.analizer.sync.DayStateService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public DailyAnalyticsService(AnalyticsFactsRepository facts,
                                 DayStateService dayStateService) {
        this.facts = facts;
        this.dayStateService = dayStateService;
    }

    /**
     * Отчёт за период.
     *
     * @param accountId аккаунт, по которому считаем; пусто — маркетплейс не подключён.
     *                  Аккаунт передаётся явно, а не ищется здесь: поиск идёт через
     *                  сессию, а значит работает только в потоке запроса. Из фоновой
     *                  задачи или теста сессии нет, и метод упал бы с отказом доступа.
     */
    @Transactional(readOnly = true)
    public DailyReport dailyReport(String marketplaceCode, Optional<Long> accountId,
                                    LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }
        // Аккаунта может ещё не быть — это не сбой, а «данных нет». Отчёт обязан сказать
        // об этом прямо (NOT_LOADED), иначе фронтенд не сможет предложить загрузку.
        if (accountId.isEmpty()) {
            PeriodCoverage nothing = PeriodCoverage.empty(from, to);
            return emptyReport(marketplaceCode, from, to, ReportCoverage.from(nothing), nothing);
        }
        Long account = accountId.get();

        PeriodCoverage periodCoverage = dayStateService.coverage(account, from, to);
        ReportCoverage coverage = ReportCoverage.from(periodCoverage);

        if (periodCoverage.isEmpty()) {
            return emptyReport(marketplaceCode, from, to, coverage, periodCoverage);
        }

        Map<Integer, String> typeNames = facts.accrualTypeNames();

        List<ProductFact> products = facts.products(account, from, to);
        List<FeeFact> fees = new ArrayList<>();
        fees.addAll(facts.deliveryFees(account, from, to));
        fees.addAll(facts.itemFees(account, from, to));
        fees.addAll(facts.nonItemFees(account, from, to));
        fees.addAll(facts.containerFees(account, from, to));
        Map<LocalDate, BigDecimal> payouts = facts.payoutsByDate(account, from, to);

        Map<LocalDate, FinancialSummary> byDate = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            byDate.put(date, FinancialSummary.empty(date, date));
        }
        for (Map.Entry<LocalDate, BigDecimal> entry : payouts.entrySet()) {
            BigDecimal payout = entry.getValue() == null ? BigDecimal.ZERO : entry.getValue();
            byDate.put(entry.getKey(), new FinancialSummary(entry.getKey(), entry.getKey(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payout));
        }

        // Товары и расходы приходят одним списком за весь период, поэтому на каждый
        // день отбираем свою часть.
        for (LocalDate date : byDate.keySet()) {
            BigDecimal payout = n(byDate.get(date).payout());
            List<ProductFact> dayProducts = filterByDate(products, date);
            List<FeeFact> dayFees = filterFeesByDate(fees, date);
            byDate.put(date, FinancialModel.summarize(date, date, dayProducts, dayFees, Map.of(date, payout)));
        }

        List<DailyRow> rows = new ArrayList<>();
        for (FinancialSummary day : byDate.values()) {
            List<FeeFact> dayFees = filterFeesByDate(fees, day.dateFrom());
            rows.add(DailyRow.of(day, FinancialModel.expensesByType(dayFees, typeNames)));
        }

        FinancialSummary total = FinancialSummary.empty(from, to);
        for (FinancialSummary day : byDate.values()) {
            total = total.plus(day);
        }

        return new DailyReport(marketplaceCode, from, to, statusOf(periodCoverage), coverage, rows,
                total.income(), total.expenses(), total.payoutValue(), total, reconciles(byDate));
    }

    private DailyReport emptyReport(String marketplaceCode, LocalDate from, LocalDate to,
                                    ReportCoverage coverage, PeriodCoverage periodCoverage) {
        List<DailyRow> rows = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            rows.add(DailyRow.of(FinancialSummary.empty(date, date),
                    new FinancialSummary.ExpenseByType(List.of())));
        }
        FinancialSummary zero = FinancialSummary.empty(from, to);
        return new DailyReport(marketplaceCode, from, to, statusOf(periodCoverage), coverage, rows,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, zero, true);
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
     */
    private boolean reconciles(Map<LocalDate, FinancialSummary> byDate) {
        return byDate.values().stream().allMatch(FinancialSummary::reconciles);
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