package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Отчёт за период.
 *
 * <p>Три основные величины — доходы, расходы и к выплате — компоненты записи, а не
 * вычисляемые методы: Jackson сериализует только компоненты, и frontend получил бы
 * отчёт без итогов.
 *
 * <p>Рядом всегда идут {@link ReportStatus} и {@link ReportCoverage}: без них отчёт
 * выглядит одинаково для «день без начислений» и «день не загружен», и нули в нём
 * читаются как «денег не было».
 *
 * <p>TODO(#response-duplication): половина ответа дублируется в двух местах, и это
 * выглядит случайностью, а не замыслом.
 *
 * <p>Деньги лежат и здесь ({@code income}, {@code expenses}, {@code payout}), и внутри
 * {@link FinancialSummary total}. Количество продано лежит только внутри {@code total},
 * хотя верхний уровень его не повторяет — так клиенту, рисующему «продано N за период»,
 * приходится знать, что одно число спрятано во вложенном объекте, а три других нет.
 * В {@link ru.analizer.analytics.domain.ProductReport.ProductRow} то же самое: {@code
 * income} и {@code expenses} дублируют значения из {@code financial}, а количество
 * внутри {@code financial} не вынесено.
 *
 * <p>Разобраться надо не раньше следующего изменения контракта: правильный вариант —
 * либо один уровень вложенности везде, либо нигде. Выносить половину сейчас опаснее, чем
 * оставить как есть: клиенты уже привыкли к обеим точкам, и перестановка ключей без
 * смены версии выглядела бы как ошибка.
 *
 * <p>{@code filter} и {@code scoped} описывают отбор: «Все товары», имя автора или
 * «SKU 123, 456». Когда отчёт ограничен отбором, {@code payout} не заполняется:
 * OZON отдаёт выплату по всему магазину, и вычленить из неё долю нескольких товаров
 * нечем. По той же причине {@code reconciled} в этом случае {@code null} — сверять
 * не с чем, и «не сходится» было бы такой же неправдой, как «сходится».
 *
 * <p>{@code unallocatedExpenses} — сумма расходов без привязки к товару, не вошедшая
 * в отчёт при отборе. Показывается, чтобы усечённая прибыль не выглядела полной.
 */
public record DailyReport(
        String marketplace,
        LocalDate dateFrom,
        LocalDate dateTo,
        ReportStatus status,
        ReportCoverage coverage,
        List<DailyRow> days,
        BigDecimal income,
        BigDecimal expenses,
        /** К выплате; {@code null}, если отчёт ограничен отбором — долю не выделить. */
        @Schema(nullable = true) BigDecimal payout,
        FinancialSummary total,
        /** Сходится ли с OZON; {@code null}, если выплата неизвестна. */
        @Schema(nullable = true) Boolean reconciled,
        ExpenseBreakdown expenseBreakdown,
        String filter,
        boolean scoped,
        BigDecimal unallocatedExpenses
) {

    /** Можно ли доверять цифрам как окончательным. */
    @com.fasterxml.jackson.annotation.JsonProperty
    public boolean isFinal() {
        return coverage.fullyFinal();
    }
}