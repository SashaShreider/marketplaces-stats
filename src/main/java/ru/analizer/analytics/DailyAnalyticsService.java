package ru.analizer.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;
import ru.analizer.sync.SyncCoverage;
import ru.analizer.sync.SyncDayService;

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
 * <p>Отчёт всегда отдаёт состояние данных ({@link ReportStatus}, {@link DataCoverage}).
 * Без этого нули за незагруженный день неотличимы от нулей за день без начислений,
 * и пользователь решил бы, что денег не было.
 */
@Service
public class DailyAnalyticsService {

    private final AnalyticsFactsRepository facts;
    private final SyncDayService syncDayService;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;

    public DailyAnalyticsService(AnalyticsFactsRepository facts,
                                 SyncDayService syncDayService,
                                 MarketplaceRepository marketplaceRepository,
                                 SellerAccountRepository sellerAccountRepository) {
        this.facts = facts;
        this.syncDayService = syncDayService;
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
    }

    @Transactional(readOnly = true)
    public DailyReport daily(String clientId, String marketplaceCode, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }
        Marketplace marketplace = marketplaceRepository.findByCode(marketplaceCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Неизвестный маркетплейс: " + marketplaceCode));

        // Аккаунта может ещё не быть — это не сбой, а «данных нет». Отчёт обязан сказать
        // об этом прямо (NOT_LOADED), иначе фронтенд не сможет предложить загрузку.
        Optional<Long> account = sellerAccountRepository
                .findByMarketplaceIdAndClientId(marketplace.getId(), clientId)
                .map(SellerAccount::getId);

        if (account.isEmpty()) {
            SyncCoverage nothing = SyncCoverage.empty(from, to);
            return emptyReport(marketplaceCode, from, to, DataCoverage.from(nothing), nothing);
        }
        Long accountId = account.get();

        SyncCoverage syncCoverage = syncDayService.coverage(accountId, from, to);
        DataCoverage coverage = DataCoverage.from(syncCoverage);

        if (syncCoverage.isEmpty()) {
            return emptyReport(marketplaceCode, from, to, coverage, syncCoverage);
        }

        Map<Integer, String> typeNames = facts.accrualTypeNames();

        List<ProductFact> products = facts.products(accountId, from, to);
        List<FeeFact> fees = new ArrayList<>();
        fees.addAll(facts.deliveryFees(accountId, from, to));
        fees.addAll(facts.itemFees(accountId, from, to));
        fees.addAll(facts.nonItemFees(accountId, from, to));
        fees.addAll(facts.containerFees(accountId, from, to));
        Map<LocalDate, BigDecimal> payouts = facts.payoutsByDate(accountId, from, to);

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

        return new DailyReport(marketplaceCode, from, to, statusOf(syncCoverage), coverage, rows,
                total.income(), total.expenses(), total.payoutValue(), total, reconciles(byDate));
    }

    private DailyReport emptyReport(String marketplaceCode, LocalDate from, LocalDate to,
                                    DataCoverage coverage, SyncCoverage syncCoverage) {
        List<DailyRow> rows = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            rows.add(DailyRow.of(FinancialSummary.empty(date, date),
                    new FinancialSummary.ExpenseByType(List.of())));
        }
        FinancialSummary zero = FinancialSummary.empty(from, to);
        return new DailyReport(marketplaceCode, from, to, statusOf(syncCoverage), coverage, rows,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, zero, true);
    }

    /**
     * Итоговый статус отчёта по покрытию периода.
     *
     * <p>Порядок важен: наличие ошибок важнее полноты, а полнота — важнее успеха.
     * Пользователь должен видеть, почему цифры неполны, а не просто «0».
     */
    public static ReportStatus statusOf(SyncCoverage coverage) {
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