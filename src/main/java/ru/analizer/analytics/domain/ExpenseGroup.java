package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Одна статья расходов вместе с расшифровкой по типам начислений.
 *
 * <p>Сумма статьи и её расшифровка лежат вместе намеренно: если клиент покажет
 * сумму, но не покажет, из чего она сложилась, проверить её будет нечем. Оба числа
 * считаются из одного списка {@code items}, поэтому разойтись не могут.
 *
 * @param category статья
 * @param label    подпись статьи; дублирует {@link ExpenseCategory#label()} и хранится
 *                 для того, чтобы клиенту не приходилось знать перечисление, —
 *                 маркетплейс со своими названиями статей подставит сюда своё
 * @param amount   сумма статьи с тем же знаком, что отдал маркетплейс: расход
 *                 отрицателен, возврат комиссии — положителен
 * @param items    расшифровка: типы начислений внутри статьи, крупные сверху
 */
public record ExpenseGroup(
        ExpenseCategory category,
        String label,
        BigDecimal amount,
        List<FinancialSummary.TypeAmount> items
) {

    public ExpenseGroup {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
