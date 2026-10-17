package ru.analizer.sync.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import ru.analizer.account.domain.AccountLookup;
import ru.analizer.sync.application.DayStateService;
import ru.analizer.sync.domain.PeriodCoverage;

import java.time.LocalDate;

/**
 * Покрытие периода: сколько дней загружено.
 *
 * <p>Отдельный контроллер, а не часть общего «состояния данных», потому что это вопрос
 * о загрузке, а не о каталоге: ответ целиком считается по таблице загруженных дней.
 *
 * <p>Ни один метод группы {@code data} и {@code analytics} не обращается к API
 * маркетплейса, поэтому такие запросы можно делать часто и сколько угодно: квота не
 * тратится.
 *
 * <p>Смысл этих ответов — не «получить отчёт», а «можно ли доверять отчёту». Ответ без
 * начислений и ответ «данных нет» выглядят одинаково, если не сказать, что произошло;
 * {@link PeriodCoverage} делает это различие явным.
 */
@RestController
@RequestMapping("/api/accounts/{accountId}/data")
public class CoverageController {

    private final DayStateService dayStateService;
    private final AccountLookup accountLookup;

    public CoverageController(DayStateService dayStateService, AccountLookup accountLookup) {
        this.dayStateService = dayStateService;
        this.accountLookup = accountLookup;
    }

    /**
     * GET /api/accounts/1/data/coverage?dateFrom=…&dateTo=…
     *
     * <p>Отвечает и до первой синхронизации: у подключённого магазина без импортов
     * покрытие нулевое, и это ответ, а не ошибка.
     *
     * <p>Чтобы узнать про один день, передайте одинаковые {@code dateFrom} и
     * {@code dateTo}. Отдельного метода «про день» нет намеренно: он был бы тем же
     * запросом с другим числом параметров.
     *
     * @param dateFrom начало периода включительно
     * @param dateTo   конец периода включительно
     * @throws ru.analizer.web.AccountNotFoundException магазина нет или он не этот
     */
    @GetMapping("/coverage")
    public PeriodCoverage coverage(
            @PathVariable Long accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {

        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }
        return dayStateService.coverage(accountLookup.requireAccount(accountId).getId(),
                dateFrom, dateTo);
    }
}