package ru.analizer.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.sync.SyncReport;
import ru.analizer.sync.SyncService;
import ru.analizer.marketplace.ozon.OzonProperties;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final OzonProperties ozonProperties;

    public SyncController(SyncService syncService, OzonProperties ozonProperties) {
        this.syncService = syncService;
        this.ozonProperties = ozonProperties;
    }

    /**
     * POST /api/sync/ozon?dateFrom=2026-04-10&dateTo=2026-04-10
     *
     * <p>Повторный вызов за тот же период не создаёт дубликатов: операции идентифицируются
     * по {@code accrual_id} в пределах аккаунта.
     */
    @PostMapping("/ozon")
    public SyncResponse syncOzon(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "false") boolean syncTypes) {

        if (syncTypes) {
            syncService.syncAccrualTypes();
        }
        SyncReport report = syncService.sync(ozonProperties.clientId(), dateFrom, dateTo);
        return new SyncResponse(report, syncTypes);
    }

    public record SyncResponse(SyncReport report, boolean typesSynchronized) {
    }
}
