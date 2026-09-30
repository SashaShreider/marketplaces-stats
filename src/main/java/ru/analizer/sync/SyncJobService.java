package ru.analizer.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.persistence.entity.JobStatus;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.entity.SyncJob;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;
import ru.analizer.persistence.repository.SyncJobRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Фоновая загрузка периода.
 *
 * <p>Зачем она нужна: загрузка месяца — это десятки обращений к OZON, а года — сотни.
 * Если делать это внутри HTTP-запроса, браузер или клиент отвалится по таймауту, хотя
 * данные наполовину загрузятся. Поэтому запрос создаёт задачу и сразу возвращает её номер,
 * а работа идёт в отдельном потоке.
 *
 * <p>Задача хранится в базе, а не только в памяти: после перезапуска приложения видно,
 * что было прервано, и загрузку можно повторить — она докачает только недостающие дни.
 *
 * <p>TODO(#scheduler): автоматический перезапуск прерванных задач при старте приложения.
 * Сейчас пользователь запускает их заново сам.
 */
@Service
public class SyncJobService {

    private static final Logger log = LoggerFactory.getLogger(SyncJobService.class);

    private final SyncJobRepository syncJobRepository;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final SyncService syncService;
    private final Clock clock;

    public SyncJobService(SyncJobRepository syncJobRepository,
                          MarketplaceRepository marketplaceRepository,
                          SellerAccountRepository sellerAccountRepository,
                          SyncService syncService,
                          Clock clock) {
        this.syncJobRepository = syncJobRepository;
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.syncService = syncService;
        this.clock = clock;
    }

    /**
     * Создаёт задачу и сразу возвращает её номер. Сама загрузка выполняется в фоне.
     *
     * @param syncTypes обновить ли справочник типов начислений перед загрузкой
     */
    @Transactional
    public SyncJobStatus submit(String clientId, String marketplaceCode,
                                LocalDate dateFrom, LocalDate dateTo, boolean syncTypes) {
        if (dateFrom == null || dateTo == null) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }

        Marketplace marketplace = marketplaceRepository.findByCode(marketplaceCode)
                .orElseThrow(() -> new IllegalArgumentException("Неизвестный маркетплейс: " + marketplaceCode));
        SellerAccount account = sellerAccountRepository
                .findByMarketplaceIdAndClientId(marketplace.getId(), clientId)
                .orElseGet(() -> sellerAccountRepository.save(
                        new SellerAccount(marketplace, marketplaceCode + " " + clientId, clientId)));

        int totalDays = (int) (dateTo.toEpochDay() - dateFrom.toEpochDay() + 1);
        SyncJob job = syncJobRepository.save(new SyncJob(
                account, marketplaceCode, dateFrom, dateTo, totalDays));

        runAsync(job.getId(), clientId, syncTypes);
        return status(job.getId()).orElseThrow(() ->
                new IllegalStateException("Задача " + job.getId() + " не найдена сразу после создания"));
    }

    /**
     * Фоновая работа. Отдельный поток: HTTP-запрос к этому моменту уже закрыт.
     */
    @Async
    public void runAsync(Long jobId, String clientId, boolean syncTypes) {
        try {
            SyncJob job = syncJobRepository.findById(jobId).orElse(null);
            if (job == null) {
                log.warn("Задача {} не найдена, запуск отменён", jobId);
                return;
            }
            job.start(Instant.now(clock));
            syncJobRepository.save(job);

            if (syncTypes) {
                syncService.syncAccrualTypes();
            }

            syncService.sync(clientId, job.getDateFrom(), job.getDateTo(), new JobProgressListener(jobId));
            finish(jobId, null);
        } catch (RuntimeException e) {
            log.error("Фоновая задача {} не выполнена", jobId, e);
            finish(jobId, e.getMessage());
        }
    }

    private void finish(Long jobId, String error) {
        syncJobRepository.findById(jobId).ifPresent(job -> {
            if (error == null) {
                job.finish(Instant.now(clock));
            } else {
                job.fail(error, Instant.now(clock));
            }
            syncJobRepository.save(job);
        });
    }

    @Transactional(readOnly = true)
    public Optional<SyncJobStatus> status(Long jobId) {
        return syncJobRepository.findById(jobId).map(this::toStatus);
    }

    @Transactional(readOnly = true)
    public java.util.List<SyncJobStatus> recent() {
        return syncJobRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toStatus)
                .toList();
    }

    private SyncJobStatus toStatus(SyncJob job) {
        return new SyncJobStatus(
                job.getId(),
                job.getMarketplaceCode(),
                job.getDateFrom(),
                job.getDateTo(),
                job.getStatus(),
                job.getTotalDays(),
                job.getDoneDays(),
                job.getSkippedDays(),
                job.getFailedDays(),
                job.getCurrentDay(),
                job.getError(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getFinishedAt(),
                job.getStatus() == JobStatus.RUNNING || job.getStatus() == JobStatus.PENDING);
    }

    /** Пишет прогресс задачи по мере загрузки дней. */
    private final class JobProgressListener implements SyncProgressListener {

        private final Long jobId;

        private JobProgressListener(Long jobId) {
            this.jobId = jobId;
        }

        @Override
        public void onStart(int totalDays, int daysToFetch) {
            touch(job -> job.currentDay(null));
        }

        @Override
        public void onDayStart(LocalDate day, int processedDays, int daysToFetch) {
            touch(job -> job.currentDay(day));
        }

        @Override
        public void onDayFailed(LocalDate day, RuntimeException error) {
            touch(SyncJob::dayFailed);
        }

        private void touch(java.util.function.Consumer<SyncJob> change) {
            syncJobRepository.findById(jobId).ifPresent(job -> {
                change.accept(job);
                syncJobRepository.save(job);
            });
        }
    }
}