package ru.analizer.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.analytics.DailyAnalyticsService;
import ru.analizer.analytics.DailyReport;
import ru.analizer.marketplace.ozon.OzonProperties;

import java.time.LocalDate;

/**
 * Ежедневная аналитика.
 *
 * <p>Отчёт строится по сохранённым операциям, а не по ответу OZON: смена правил расчёта
 * не должна требовать повторной загрузки данных.
 */
@RestController
@RequestMapping("/api/analytics")
public class DailyAnalyticsController {

    private final DailyAnalyticsService analytics;
    private final OzonProperties ozonProperties;

    public DailyAnalyticsController(DailyAnalyticsService analytics, OzonProperties ozonProperties) {
        this.analytics = analytics;
        this.ozonProperties = ozonProperties;
    }

    /**
     * GET /api/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-12
     *
     * <p>Основные колонки отчёта — доходы, расходы и к выплате. В каждой строке и в
     * итоге дополнительно отдаётся разбивка по составляющим и детализация расходов
     * по типам начислений: подробный отчёт собирается из неё позже без изменения API.
     *
     * @param clientId аккаунт продавца; по умолчанию — из конфигурации. Параметр оставлен
     *                 на будущее, поскольку модель допускает несколько аккаунтов.
     */
    @GetMapping("/daily")
    public DailyReport daily(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String clientId) {

        String account = clientId == null || clientId.isBlank()
                ? ozonProperties.clientId()
                : clientId;
        return analytics.daily(account, "OZON", dateFrom, dateTo);
    }
}