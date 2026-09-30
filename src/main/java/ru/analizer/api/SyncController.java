package ru.analizer.api;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.marketplace.ozon.OzonProperties;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;
import ru.analizer.sync.SyncCoverage;
import ru.analizer.sync.SyncDayService;
import ru.analizer.sync.SyncJobService;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Синхронизация и состояние данных.
 *
 * <p>Загрузка запускается только явной командой: GET-запрос отчёта никогда сам не ходит
 * в OZON. Иначе пользователь не понимал бы, куда делось время и почему отчёт не отвечает.
 */
@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncJobService syncJobService;
    private final SyncDayService syncDayService;
    private final OzonProperties ozonProperties;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;

    public SyncController(SyncJobService syncJobService,
                          SyncDayService syncDayService,
                          OzonProperties ozonProperties,
                          MarketplaceRepository marketplaceRepository,
                          SellerAccountRepository sellerAccountRepository) {
        this.syncJobService = syncJobService;
        this.syncDayService = syncDayService;
        this.ozonProperties = ozonProperties;
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
    }

    /**
     * POST /api/sync/ozon?dateFrom=…&dateTo=…
     *
     * <p>Возвращает 202 и номер задачи сразу: сама загрузка идёт в фоне. Догружаются
     * только недостающие дни — повторный запуск не создаёт дублей и не ходит в OZON зря.
     */
    @PostMapping("/ozon")
    public ResponseEntity<ru.analizer.sync.SyncJobStatus> syncOzon(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "false") boolean syncTypes,
            @RequestParam(required = false) String clientId) {

        String account = clientId == null || clientId.isBlank() ? ozonProperties.clientId() : clientId;
        ru.analizer.sync.SyncJobStatus status = syncJobService.submit(
                account, "OZON", dateFrom, dateTo, syncTypes);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }

    /** GET /api/sync/status?jobId=… — прогресс фоновой загрузки. */
    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestParam(required = false) Long jobId) {
        if (jobId != null) {
            return syncJobService.status(jobId)
                    .map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.notFound().build());
        }
        List<ru.analizer.sync.SyncJobStatus> recent = syncJobService.recent();
        return ResponseEntity.ok(recent.isEmpty() ? List.of() : recent.getFirst());
    }

    /** GET /api/sync/jobs — последние задачи. */
    @GetMapping("/jobs")
    public List<ru.analizer.sync.SyncJobStatus> jobs() {
        return syncJobService.recent();
    }

    /**
     * Покрытие периода: что уже загружено, а что нет.
     *
     * <p>Отвечает и до первой синхронизации: отсутствие аккаунта — это «ничего не
     * загружено», а не ошибка. Иначе фронтенд не смог бы спросить, что уже есть,
     * именно в тот момент, когда это и нужно.
     */
    @GetMapping("/coverage")
    public SyncCoverage coverage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String clientId) {

        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }
        return syncDayService.coverage(findAccountId(clientId).orElse(null), dateFrom, dateTo);
    }

    /**
     * GET /api/sync/day?date=… — что известно про один день.
     */
    @GetMapping("/day")
    public ResponseEntity<?> day(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String clientId) {

        var account = findAccountId(clientId);
        if (account.isEmpty()) {
            return ResponseEntity.ok(syncDayService.coverage(null, date, date));
        }
        SyncCoverage coverage = syncDayService.coverage(account.get(), date, date);
        if (coverage.loadedDays() == 0 && coverage.failedDates().isEmpty()) {
            return ResponseEntity.ok(coverage);
        }
        return ResponseEntity.ok(coverage);
    }

    private Optional<Long> findAccountId(String clientId) {
        String account = clientId == null || clientId.isBlank() ? ozonProperties.clientId() : clientId;
        return marketplaceRepository.findByCode("OZON")
                .flatMap(marketplace -> sellerAccountRepository
                        .findByMarketplaceIdAndClientId(marketplace.getId(), account))
                .map(SellerAccount::getId);
    }
}