package ru.analizer.analytics.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * Динамика продаж по дням периода.
 *
 * <p>Отдельный отчёт от отчёта по товарам, а не поле в нём: у того строки это
 * товары и суммы денег, а здесь — количество единиц по дням. Смешивать их в одном
 * ответе означало бы отправлять клиенту лишние данные, а он всё равно рисует по
 * дням, а не по товарам.
 *
 * <p>Дни с нулём пропускать нельзя: у графика они разные при отсутствии продаж и
 * при незагруженном периоде, и без состояния дня эти случаи выглядели бы
 * одинаково. Состояние приходит в {@link #coverage()} тем же способом, что и в
 * остальных отчётах.
 *
 * @param dateFrom начало периода включительно
 * @param dateTo   конец периода включительно
 * @param status   готов ли период к просмотру
 * @param coverage состояние данных за период: какие дни не загружены и какие ещё уточняются
 * @param days     продажи по дням; дни без операций сюда не попадают
 * @param filter   чем ограничен отчёт, для заголовка над графиком
 */
public record SalesDynamicsReport(
        LocalDate dateFrom,
        LocalDate dateTo,
        ReportStatus status,
        ReportCoverage coverage,
        List<SalesDay> days,
        String filter
) {

    public SalesDynamicsReport {
        days = days == null ? List.of() : List.copyOf(days);
    }

    /**
     * Один день динамики.
     *
     * @param date     день
     * @param sold     продано единиц
     * @param returned возвращено единиц
     */
    public record SalesDay(LocalDate date, int sold, int returned) {
    }
}
