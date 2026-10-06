package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Финансовая модель: превращает сохранённые операции в показатели отчёта.
 *
 * <p>Класс чистый — ни запросов, ни обращений к базе. Все правила собраны здесь, и их
 * можно проверить на реальных выгрузках без Spring.
 *
 * <h2>Что считается продажей</h2>
 * <p>Продажа — это строка операции POSTING, у которой OZON прислал {@code sale_price}.
 * Три случая различаются, и их нельзя смешивать:
 * <ul>
 *   <li><b>{@code sale_price > 0}</b> — продажа. Единиц столько, сколько в
 *       {@code quantity}; при количестве больше единицы {@code sale_price} приходит за
 *       всю позицию, а не за штуку.</li>
 *   <li><b>{@code sale_price < 0}</b> — возврат. Считается отдельно и в проданные не
 *       входит. {@code quantity} у возврата положителен: уменьшение несут деньги, а не
 *       количество.</li>
 *   <li><b>{@code sale_price} отсутствует</b> — не продажа вовсе. Такие строки у OZON
 *       приходят для удержаний и штрафов за доставку: {@code commission} у них равен
 *       {@code null}, а вся отрицательная сумма операции равна логистике. Их количество
 *       равно единице, поэтому включение в счёт дало бы фиктивные продажи.</li>
 * </ul>
 *
 * <h2>Правила</h2>
 * <ul>
 *   <li><b>Продажи</b> — {@code posting_product.sale_price &gt; 0}.</li>
 *   <li><b>Возвраты</b> — {@code sale_price &lt; 0}. Знак сохраняется отрицательным:
 *       в отчёте возвраты уменьшают доход.</li>
 *   <li><b>Начисления по программе партнёров</b> — {@code bonus} и {@code coinvestment}.
 *       Это доход, а не расход: OZON платит их продавцу сверх цены товара.
 *       Если потерять эти два потока, «к выплате» разойдётся ровно на их сумму —
 *       на настоящих выгрузках September 2026 это около 140 тысяч.</li>
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
        int soldQuantity = 0;
        int returnedQuantity = 0;

        for (ProductFact product : products) {
            BigDecimal salePrice = product.salePrice();
            // Бонусы и комиссия считаются по всем строкам: у строки без sale_price они
            // всё равно нулевые, но полагаться на это — значит связать корректность
            // денег с тем, придёт ли OZON лишнее поле.
            partnerProgramme = partnerProgramme
                    .add(n(product.bonus()))
                    .add(n(product.coinvestment()));
            commission = commission.add(n(product.saleCommission()));

            // sale_price отсутствует там, где OZON не прислал commission вовсе. Это не
            // ноль и не продажа за ноль: строки такого вида — удержания и штрафы за
            // доставку. Их количество равно единице, и включение в счёт дало бы
            // завышение примерно на двадцать процентов.
            if (salePrice == null) {
                continue;
            }

            if (salePrice.signum() > 0) {
                sales = sales.add(salePrice);
                soldQuantity += n(product.quantity());
            } else {
                // Знак количества у возврата остаётся положительным: OZON присылает
                // quantity = 1 и для возврата, уменьшение несут деньги. Возвраты считаются
                // отдельно и в soldQuantity не входят.
                returns = returns.add(salePrice);
                returnedQuantity += n(product.quantity());
            }
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
                commission, logistics, otherExpenses, payout, soldQuantity, returnedQuantity);
    }

    private static int n(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * Расходы по типам начислений. В отчёт попадают только те, что не являются
     * комиссией маркетплейса и не относятся к строке товара, — то есть ITEM, NON_ITEM
     * и CONTAINER_FEES. Именно их клиенту и полезно видеть отдельно: по ним видно,
     * из чего складываются прочие расходы.
     *
     * <p>Подписи берутся из справочника маркетплейса целиком, вместе с описанием.
     * Одного имени мало: {@code PayPerClick} или {@code StarsMembers} — служебные
     * идентификаторы, по которым продавец не поймёт, за что он платит.
     *
     * <p>Тип, которого нет в справочнике, не теряется, а подписывается своим
     * номером: показать «тип 12345» честнее, чем не показать расход вовсе.
     */
    public static FinancialSummary.ExpenseByType expensesByType(
            List<FeeFact> fees,
            Map<Integer, TypeLabels> typeLabels) {
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
                .forEach(e -> {
                    TypeLabels labels = typeLabels.get(e.getKey());
                    items.add(new FinancialSummary.TypeAmount(
                            e.getKey(),
                            labels == null ? "Тип " + e.getKey() : labels.name(),
                            labels == null ? null : labels.description(),
                            e.getValue()));
                });
        return new FinancialSummary.ExpenseByType(items);
    }

    /**
     * Подписи типа начисления из справочника маркетплейса.
     *
     * @param name        служебное название, например {@code PayPerClick}
     * @param description человеческое описание, например «Оплата за показы»
     */
    public record TypeLabels(String name, String description) {
    }

    private static BigDecimal n(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
