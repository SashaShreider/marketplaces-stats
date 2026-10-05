package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.analizer.integration.ozon.dto.finance.Commission;
import ru.analizer.integration.ozon.dto.finance.Delivery;
import ru.analizer.sync.infrastructure.entity.Posting;

/**
 * Финансовая модель: превращает сохранённые операции в показатели отчёта.
 *
 * <p>Класс чистый — ни запросов, ни обращений к базе. Все правила собраны здесь, и их
 * можно проверить на реальных выгрузках без Spring.
 *
 * <h2>Правила</h2>
 * <ul>
 *   <li><b>Продажи</b> — {@code posting_product.sale_price &gt; 0}.</li>
 *   <li><b>Возвраты</b> — {@code sale_price &lt; 0}. Знак сохраняется отрицательным:
 *       в отчёте возвраты уменьшают доход.</li>
 *   <li><b>Начисления по программе партнёров</b> — {@code bonus} и {@code coinvestment}.
 *       Это доход, а не расход: OZON платит их продавцу сверх цены товара.
 *       В IMPLEMENTATION.md §15 эти два потока не перечислены, из-за чего «к выплате»
 *       разошёлся бы ровно на их сумму.</li>
 *   <li><b>Комиссия</b> — {@code sale_commission}. При возврате она положительна:
 *       комиссия возвращается продавцу.</li>
 *   <li><b>Логистика</b> — сумма {@code delivery_service}.</li>
 *   <li><b>Прочие расходы</b> — ITEM, NON_ITEM и CONTAINER_FEES. Списания, связанные
 *       с обработкой возврата, попадают сюда же: их тип ничем не отличается от прочих.</li>
 *   <li><b>К выплате</b> — сумма {@code finance_accrual.total_amount}, а не пересчёт.
 *       Так отчёт не расходится с кабинетом OZON.</li>
 * </ul>
 */
public final class FinancialModel {

    private FinancialModel() {
    }

    /**
     * Сводит операции периода в показатели.
     *
     * @param dateFrom начало периода
     * @param dateTo   конец периода
     * @param products строки товаров (POSTING)
     * @param fees     строки расходов с типом начисления
     * @param payouts  готовые итоги по дням: дата → сумма total_amount
     */
    public static FinancialSummary summarize(LocalDate dateFrom,
                                             LocalDate dateTo,
                                             List<ProductFact> products,
                                             List<FeeFact> fees,
                                             Map<LocalDate, BigDecimal> payouts) {
        BigDecimal sales = BigDecimal.ZERO;
        BigDecimal returns = BigDecimal.ZERO;
        BigDecimal partnerProgramme = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal logistics = BigDecimal.ZERO;
        BigDecimal otherExpenses = BigDecimal.ZERO;

        for (ProductFact product : products) {
            BigDecimal salePrice = n(product.salePrice());
            if (salePrice.signum() > 0) {
                sales = sales.add(salePrice);
            } else {
                returns = returns.add(salePrice);
            }
            partnerProgramme = partnerProgramme
                    .add(n(product.bonus()))
                    .add(n(product.coinvestment()));
            commission = commission.add(n(product.saleCommission()));
        }

        for (FeeFact fee : fees) {
            BigDecimal amount = n(fee.amount());
            switch (fee.kind()) {
                case DELIVERY -> logistics = logistics.add(amount);
                // ITEM, NON_ITEM и CONTAINER — это «прочие расходы» отчёта.
                // Разделение по типу начисления хранится отдельно, чтобы подробный
                // отчёт можно было построить позже без изменения модели.
                case ITEM, NON_ITEM, CONTAINER -> otherExpenses = otherExpenses.add(amount);
            }
        }

        BigDecimal payout = payouts == null ? BigDecimal.ZERO
                : payouts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinancialSummary(dateFrom, dateTo, sales, returns, partnerProgramme,
                commission, logistics, otherExpenses, payout);
    }

    /**
     * Расходы по типам начислений. В отчёт попадают только те, что не являются
     * комиссией маркетплейса и не относятся к строке товара, — то есть ITEM, NON_ITEM
     * и CONTAINER_FEES: именно их SPEC.md требует детализировать.
     */
    public static FinancialSummary.ExpenseByType expensesByType(List<FeeFact> fees,
                                                                Map<Integer, String> typeNames) {
        Map<Integer, BigDecimal> byType = new LinkedHashMap<>();
        for (FeeFact fee : fees) {
            if (fee.kind() == FeeFact.FeeKind.DELIVERY) {
                continue;
            }
            if (fee.typeId() == null) {
                continue;
            }
            byType.merge(fee.typeId(), n(fee.amount()), BigDecimal::add);
        }
        List<FinancialSummary.TypeAmount> items = new ArrayList<>();
        // Расходы отрицательные, поэтому «самый крупный» — это наименьшее значение.
        // Сортируем по возрастанию, чтобы в отчёте первыми шли главные статьи.
        byType.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .forEach(e -> items.add(new FinancialSummary.TypeAmount(
                        e.getKey(), typeNames.get(e.getKey()), e.getValue())));
        return new FinancialSummary.ExpenseByType(items);
    }

    private static BigDecimal n(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
