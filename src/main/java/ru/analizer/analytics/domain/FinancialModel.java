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

    /** Подпись строки комиссии: у неё нет типа начисления, она лежит в строке товара. */
    private static final String COMMISSION_ITEM = "Комиссия за продажу";

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
     * Расходы периода, разложенные по крупным статьям.
     *
     * <p>В отличие от {@link #expensesByType} покрывает расходы целиком: комиссия и
     * логистика тоже получают свои статьи, а не остаются за рамками расшифровки.
     * Внутри статьи — те же типы начислений, что продавец видит в кабинете.
     *
     * <p>Комиссия не приходит отдельным начислением: она лежит в строке товара
     * ({@code posting_product.sale_commission}), поэтому передаётся отдельным
     * аргументом. Знак у неё тот же, что у остальных расходов: удержание
     * отрицательно, возврат комиссии при возврате товара — положителен.
     *
     * <p>Статья определяется по названию типа, а не по его номеру: справочник
     * обновляется из API, и номеров может стать больше. Логистика — запасной
     * вариант: туда попадают услуги из блока {@link FeeFact.FeeKind#DELIVERY},
     * если их тип не отнесён к другой статье по названию.
     *
     * @param fees       строки расходов с типом начисления
     * @param commission комиссия маркетплейса за период; {@code null} — не было
     * @param typeLabels подписи типов из справочника маркетплейса
     */
    public static ExpenseBreakdown expenseBreakdown(List<FeeFact> fees,
                                                    BigDecimal commission,
                                                    Map<Integer, TypeLabels> typeLabels) {
        Map<ExpenseCategory, Map<Integer, BigDecimal>> byCategory = new LinkedHashMap<>();
        for (FeeFact fee : fees) {
            // Сначала смотрим название типа: явно отнесённый к статье тип важнее
            // источника. Логистика — запасной вариант для услуг доставки, которых
            // нет в списках выше: в неё попадает только то, что OZON прислал в
            // блоке delivery.services, и ничего добавленного вручную.
            ExpenseCategory category = ExpenseCategory.byTypeName(nameOf(fee.typeId(), typeLabels));
            if (category == ExpenseCategory.OTHER && fee.kind() == FeeFact.FeeKind.DELIVERY) {
                category = ExpenseCategory.LOGISTICS;
            }
            byCategory.computeIfAbsent(category, c -> new LinkedHashMap<>())
                    .merge(fee.typeId(), n(fee.amount()), BigDecimal::add);
        }

        List<ExpenseGroup> groups = new ArrayList<>();
        for (ExpenseCategory category : ExpenseCategory.displayOrder()) {
            List<FinancialSummary.TypeAmount> items = new ArrayList<>();
            if (category == ExpenseCategory.COMMISSION && n(commission).signum() != 0) {
                items.add(new FinancialSummary.TypeAmount(null, COMMISSION_ITEM, null, commission));
            }
            Map<Integer, BigDecimal> types = byCategory.get(category);
            if (types != null) {
                // Расходы отрицательные, поэтому «самый крупный» — наименьшее значение.
                types.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue())
                        .filter(e -> n(e.getValue()).signum() != 0)
                        .forEach(e -> items.add(typeAmount(e.getKey(), e.getValue(), typeLabels)));
            }
            if (items.isEmpty()) {
                continue;
            }
            // Статья, которой магазин не пользуется, в ответ не попадает: ноль в ней
            // читался бы как поломка, а не как «этого не было».
            groups.add(new ExpenseGroup(category, category.label(),
                    sumOf(items), items));
        }
        return new ExpenseBreakdown(groups, sumOfGroups(groups));
    }

    private static FinancialSummary.TypeAmount typeAmount(Integer typeId,
                                                          BigDecimal amount,
                                                          Map<Integer, TypeLabels> typeLabels) {
        TypeLabels labels = typeId == null ? null : typeLabels.get(typeId);
        if (labels == null) {
            // Расход без подписи нельзя выбросить: сумма статей разошлась бы с итогом,
            // и это выглядело бы как ошибка расчёта. Показать номер честнее.
            return new FinancialSummary.TypeAmount(typeId,
                    typeId == null ? "Без типа начисления" : "Тип " + typeId,
                    null, amount);
        }
        return new FinancialSummary.TypeAmount(typeId, labels.name(), labels.description(), amount);
    }

    private static String nameOf(Integer typeId, Map<Integer, TypeLabels> typeLabels) {
        TypeLabels labels = typeId == null ? null : typeLabels.get(typeId);
        return labels == null ? null : labels.name();
    }

    private static BigDecimal sumOf(List<FinancialSummary.TypeAmount> items) {
        return items.stream()
                .map(FinancialSummary.TypeAmount::amount)
                .map(FinancialModel::n)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal sumOfGroups(List<ExpenseGroup> groups) {
        return groups.stream()
                .map(ExpenseGroup::amount)
                .map(FinancialModel::n)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
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
