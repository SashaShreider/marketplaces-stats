package ru.analizer.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ежедневная аналитика за произвольный период.
 *
 * <p>Считает по сохранённым операциям, а не по ответу OZON: отчёт должен оставаться
 * прежним при смене правил расчёта.
 */
@Service
public class DailyAnalyticsService {

    private final AnalyticsFactsRepository facts;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;

    public DailyAnalyticsService(AnalyticsFactsRepository facts,
                                 MarketplaceRepository marketplaceRepository,
                                 SellerAccountRepository sellerAccountRepository) {
        this.facts = facts;
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

        Long accountId = resolveAccount(clientId, marketplaceCode);
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
        for (Map.Entry<LocalDate, BigDecimal> e : payouts.entrySet()) {
            BigDecimal payout = e.getValue() == null ? BigDecimal.ZERO : e.getValue();
            byDate.put(e.getKey(), new FinancialSummary(e.getKey(), e.getKey(),
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, payout));
        }

        // Сводим по дням. Товары и расходы приходят одним списком за весь период,
        // поэтому на каждый день отбираем свою часть.
        for (LocalDate date : byDate.keySet()) {
            BigDecimal payout = n(byDate.get(date).payout());
            List<ProductFact> dayProducts = filterByDate(products, date);
            List<FeeFact> dayFees = filterFeesByDate(fees, date);

            FinancialSummary day = FinancialModel.summarize(
                    date, date, dayProducts, dayFees, Map.of(date, payout));
            byDate.put(date, day);
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

        return new DailyReport(marketplaceCode, from, to, rows,
                total.income(), total.expenses(), total.payoutValue(), total, reconciles(byDate));
    }

    /**
     * Проверка, что по каждому дню доходы минус расходы равны данным OZON.
     * Ненулевое расхождение означало бы, что мы не учли какой-то вид начисления —
     * такой день в отчёте помечается, а не замалчивается.
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

    private Long resolveAccount(String clientId, String marketplaceCode) {
        Marketplace marketplace = marketplaceRepository.findByCode(marketplaceCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Неизвестный маркетплейс: " + marketplaceCode));
        return sellerAccountRepository
                .findByMarketplaceIdAndClientId(marketplace.getId(), clientId)
                .map(SellerAccount::getId)
                .orElseThrow(() -> new IllegalStateException(
                        "Аккаунт продавца " + clientId + " не найден — сначала выполните синхронизацию"));
    }

    private static BigDecimal n(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Строка отчёта за один день. */
    public record DailyRow(
            LocalDate date,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal payout,
            Breakdown breakdown,
            List<FinancialSummary.TypeAmount> expensesByType
    ) {
        public static DailyRow of(FinancialSummary day,
                                  FinancialSummary.ExpenseByType byType) {
            return new DailyRow(day.dateFrom(), day.income(), day.expenses(), day.payoutValue(),
                    new Breakdown(day.sales(), day.returns(), day.partnerProgramme(),
                            day.commission(), day.logistics(), day.otherExpenses()),
                    byType.items());
        }

        /**
         * Детализация показателей дня. Отдаём её вместе с итогом, чтобы подробный
         * отчёт можно было построить позже, не переделывая ни модель, ни API.
         */
        public record Breakdown(
                BigDecimal sales,
                BigDecimal returns,
                BigDecimal partnerProgramme,
                BigDecimal commission,
                BigDecimal logistics,
                BigDecimal otherExpenses
        ) {
        }
    }

    /**
 * Отчёт за период.
 *
 * <p>Три основные величины — компоненты записи, а не вычисляемые методы: Jackson
 * сериализует только компоненты, и frontend получил бы отчёт без итогов.
 * Полная раскладка отдаётся рядом, чтобы подробный отчёт собрать позже.
 */
public record DailyReport(
        String marketplace,
        LocalDate dateFrom,
        LocalDate dateTo,
        List<DailyRow> days,
        BigDecimal income,
        BigDecimal expenses,
        BigDecimal payout,
        FinancialSummary total,
        boolean reconciled
) {
}
}
