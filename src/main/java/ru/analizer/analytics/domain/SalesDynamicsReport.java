package ru.analizer.analytics.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Динамика продаж по дням периода.
 *
 * <p>Отдельный отчёт от отчёта по товарам, а не поле в нём: у того строки это
 * товары и суммы денег, а здесь — день, количество единиц и деньги этого дня.
 * Смешивать их в одном ответе означало бы отправлять клиенту лишние данные: графику
 * по дням не нужны строки товаров, а таблице товаров — разбивка по датам.
 *
 * <p>Дни с нулём пропускать нельзя: у графика они разные при отсутствии продаж и
 * при незагруженном периоде, и без состояния дня эти случаи выглядели бы
 * одинаково. Состояние приходит в {@link #coverage()} тем же способом, что и в
 * остальных отчётах.
 *
 * <p>Деньги считаются по тем же правилам, что и в отчёте по товарам, — через
 * {@link FinancialModel} и только по начислениям, у которых есть SKU. Расходы без
 * привязки к товару (NON_ITEM, CONTAINER) распределить между книгами нельзя, поэтому
 * в дни и в выручку они не входят: иначе выручка дня не сходилась бы с суммой выручки
 * товаров, а вопрос у двух таблиц один — сколько заработала книга. Сумма таких
 * расходов за период приходит отдельным полем {@link #unallocatedExpenses}, как и в
 * отчёте по товарам.
 *
 * @param dateFrom            начало периода включительно
 * @param dateTo              конец периода включительно
 * @param status              готов ли период к просмотру
 * @param coverage            состояние данных за период: какие дни не загружены и какие ещё уточняются
 * @param days                показатели по дням; дни без операций сюда не попадают
 * @param filter              чем ограничен отчёт, для заголовка над графиком
 * @param unallocatedExpenses расходы периода без привязки к SKU, по модулю: в выручку
 *                            они не входят, но и молчать о них нельзя — иначе расходы
 *                            динамики не сойдутся с дневным отчётом
 */
public record SalesDynamicsReport(
        LocalDate dateFrom,
        LocalDate dateTo,
        ReportStatus status,
        ReportCoverage coverage,
        List<SalesDay> days,
        String filter,
        BigDecimal unallocatedExpenses
) {

    public SalesDynamicsReport {
        days = days == null ? List.of() : List.copyOf(days);
        unallocatedExpenses = unallocatedExpenses == null ? BigDecimal.ZERO : unallocatedExpenses;
    }

    /**
     * Один день динамики: количество единиц и деньги.
     *
     * <p>Денежные поля идут со знаком, как их отдаёт OZON: доход положителен,
     * комиссия, логистика и списания отрицательны. Так же устроены остальные отчёты,
     * и клиенту не приходится угадывать, к какому знаку приводить число.
     *
     * <p>В расходы дня входят только начисления с привязкой к SKU. Списания без
     * товара — это расходы магазина, а не книги, и в выручке дня им не место.
     *
     * @param date          день
     * @param sold          продано единиц
     * @param returned      возвращено единиц
     * @param income        доходы: цена продаж за вычетом возвратов плюс бонусы и ковейст
     * @param commission    комиссия маркетплейса
     * @param logistics     логистика
     * @param promotion     продвижение: Promotion, PayPerClick и StarsMembership
     * @param otherExpenses прочие расходы, уже без продвижения
     * @param revenue       выручка: доходы минус все расходы, включая продвижение
     */
    public record SalesDay(
            LocalDate date,
            int sold,
            int returned,
            BigDecimal income,
            BigDecimal commission,
            BigDecimal logistics,
            BigDecimal promotion,
            BigDecimal otherExpenses,
            BigDecimal revenue
    ) {

        public SalesDay {
            income = n(income);
            commission = n(commission);
            logistics = n(logistics);
            promotion = n(promotion);
            otherExpenses = n(otherExpenses);
            revenue = n(revenue);
        }

        /** День без операций: нули по всем показателям. */
        public static SalesDay empty(LocalDate date) {
            return new SalesDay(date, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        private static BigDecimal n(BigDecimal value) {
            return value == null ? BigDecimal.ZERO : value;
        }
    }
}
