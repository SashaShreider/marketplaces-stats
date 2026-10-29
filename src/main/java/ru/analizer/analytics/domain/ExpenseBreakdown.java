package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.util.List;

/**
 * Расходы периода, разложенные по статьям.
 *
 * <p>Заменяет прежнюю детализацию «по типам начислений»: та отдавала только ITEM,
 * NON_ITEM и CONTAINER, то есть без комиссии и без логистики, и рядом с полными
 * расходами выглядела как их часть. Здесь статьи покрывают расходы целиком:
 *
 * <pre>
 * комиссия + логистика + прочие = все расходы отчёта
 * </pre>
 *
 * <p>Равенство проверяется тестом на настоящих выгрузках. Без него карточка
 * расходов и итог отчёта разошлись бы, и заметить это можно было бы только глазами.
 *
 * @param groups статьи в порядке показа; пустые статьи не перечисляются — ноль
 *               в статье, которой магазин не пользуется, читается как поломка
 * @param total  сумма статей со знаком, как его отдаёт маркетплейс. Это не
 *               {@link FinancialSummary#expenses()}: тот берёт модули, а здесь
 *               возврат комиссии уменьшает расход, как и должен
 */
public record ExpenseBreakdown(List<ExpenseGroup> groups, BigDecimal total) {

    public ExpenseBreakdown {
        groups = groups == null ? List.of() : List.copyOf(groups);
        total = total == null ? BigDecimal.ZERO : total;
    }

    /** Пустая раскладка: расходов не было. */
    public static ExpenseBreakdown empty() {
        return new ExpenseBreakdown(List.of(), BigDecimal.ZERO);
    }

    /** Статья по её виду; {@code null}, если расходов этой статьи не было. */
    public ExpenseGroup group(ExpenseCategory category) {
        return groups.stream()
                .filter(g -> g.category() == category)
                .findFirst()
                .orElse(null);
    }
}
