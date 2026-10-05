package ru.analizer.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.analizer.catalog.CatalogProgressListener;
import ru.analizer.catalog.CatalogSyncReport;
import ru.analizer.catalog.CatalogSyncService;
import ru.analizer.persistence.entity.JobStatus;
import ru.analizer.persistence.entity.JobType;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.entity.SyncJob;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;
import ru.analizer.persistence.repository.SyncJobRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Фоновая загрузка периода.
 *
 * <p>Зачем она нужна: загрузка месяца — это десятки обращений к OZON, а года — сотни.
 * Если делать это внутри HTTP-запроса, клиент отвалится по таймауту, хотя данные наполовину
 * загрузятся. Поэтому запрос создаёт задачу и сразу возвращает её номер, а работа уходит
 * в отдельный поток.
 *
 * <p>Два решения сделаны явно, без опоры на прокси Spring:
 * <ul>
 *   <li>фон — {@link TaskExecutor}, потому что {@code @Async} работает только при вызове
 *       через прокси, и прямой вызов метода того же класса выполнялся бы синхронно
 *       (POST-запрос «зависал» бы на все 30 дней);</li>
 *   <li>транзакции — {@link TransactionTemplate} по той же причине: {@code @Transactional}
 *       на собственном методе не срабатывает.</li>
 * </ul>
 * Явная граница видна в коде и не ломается следующим же рефакторингом.
 *
 * <p>Две задачи, пересекающиеся по датам, запустить нельзя: они писали бы одни и те же
 * операции. Проверка и создание задачи выполняются под одним монитором, поэтому два
 * одновременных POST-запроса не пройдут оба.
 *
 * <p>TODO(#multi-instance): защита от гонки опирается на память одного процесса. При
 * нескольких экземплярах приложения блокировку нужно перенести в базу — например,
 * частичным уникальным индексом на активные задачи.
 */
@Service
public class SyncJobService {

    private static final Logger log = LoggerFactory.getLogger(SyncJobService.class);

    private final SyncJobRepository syncJobRepository;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final SyncService syncService;
    private final CatalogSyncService catalogSyncService;
    private final TaskExecutor taskExecutor;
    private final TransactionTemplate tx;
    private final Clock clock;

    /**
     * Монитор создания задач: защищает проверку пересечения и вставку строки от гонки.
     */
    private final Object submitLock = new Object();

    public SyncJobService(SyncJobRepository syncJobRepository,
                          MarketplaceRepository marketplaceRepository,
                          SellerAccountRepository sellerAccountRepository,
                          SyncService syncService,
                          CatalogSyncService catalogSyncService,
                          @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                          PlatformTransactionManager transactionManager,
                          Clock clock) {
        this.syncJobRepository = syncJobRepository;
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.syncService = syncService;
        this.catalogSyncService = catalogSyncService;
        this.taskExecutor = taskExecutor;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Создаёт задачу и сразу возвращает её номер. Сама загрузка выполняется в фоне.
     *
     * @param syncTypes обновить ли справочник типов начислений перед загрузкой
     * @throws PeriodAlreadySyncingException если по этому аккаунту уже идёт загрузка,
     *                                    пересекающаяся с запрошенным периодом
     */
    public SyncJobStatus submit(String clientId, String marketplaceCode,
                                LocalDate dateFrom, LocalDate dateTo, boolean syncTypes) {
        if (dateFrom == null || dateTo == null) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }

        Long jobId;
        synchronized (submitLock) {
            Long accountId = tx.execute(status ->
                    resolveAccount(clientId, marketplaceCode).getId());
            if (accountId == null) {
                throw new IllegalStateException("Не удалось определить аккаунт продавца");
            }

            Optional<SyncJob> active = findOverlapping(accountId, dateFrom, dateTo);
            if (active.isPresent()) {
                throw new PeriodAlreadySyncingException(
                        describe(active.get()), dateFrom, dateTo);
            }

            SyncJob job = tx.execute(status -> syncJobRepository.save(new SyncJob(
                    resolveAccount(clientId, marketplaceCode), marketplaceCode, JobType.FINANCE,
                    dateFrom, dateTo, countDays(dateFrom, dateTo))));
            if (job == null) {
                throw new IllegalStateException("Не удалось создать задачу синхронизации");
            }
            jobId = job.getId();
        }

        // Запуск после выхода из монитора: иначе фоновая работа могла бы начаться
        // и занять его раньше, чем проверка завершится.
        taskExecutor.execute(() -> runJob(jobId, clientId, syncTypes));

        return status(jobId).orElseThrow(() ->
                new IllegalStateException("Задача " + jobId + " не найдена сразу после создания"));
    }

    /**
     * Задача загрузки каталога товаров.
     *
     * <p>Даты не задаются и не проверяются: у каталога их нет. Блокировка своя —
     * достаточно, чтобы активная задача каталога была одна, иначе два процесса
     * переписывали бы одни и те же товары.
     */
    public SyncJobStatus submitCatalog(String clientId, String marketplaceCode) {
        Long jobId;
        synchronized (submitLock) {
            Long accountId = tx.execute(status ->
                    resolveAccount(clientId, marketplaceCode).getId());
            if (accountId == null) {
                throw new IllegalStateException("Не удалось определить аккаунт продавца");
            }
            Optional<SyncJob> active = tx.execute(status ->
                    syncJobRepository.findFirstActiveOfType(accountId, JobType.CATALOG));
            if (active.isPresent()) {
                throw new IllegalStateException(
                        "Загрузка каталога уже выполняется: задача №" + active.get().getId());
            }

            SyncJob job = tx.execute(status -> syncJobRepository.save(new SyncJob(
                    resolveAccount(clientId, marketplaceCode), marketplaceCode,
                    JobType.CATALOG, null, null, 0)));
            if (job == null) {
                throw new IllegalStateException("Не удалось создать задачу загрузки каталога");
            }
            jobId = job.getId();
        }

        taskExecutor.execute(() -> runCatalogJob(jobId, clientId));

        return status(jobId).orElseThrow(() ->
                new IllegalStateException("Задача " + jobId + " не найдена сразу после создания"));
    }

    void runCatalogJob(Long jobId, String clientId) {
        if (syncJobRepository.findById(jobId).isEmpty()) {
            log.warn("Задача {} не найдена, загрузка каталога отменена", jobId);
            return;
        }
        try {
            touch(jobId, job -> job.start(Instant.now(clock)));
            CatalogSyncReport report = catalogSyncService.sync(clientId, new CatalogJobProgress(jobId));
            touch(jobId, job -> job.setTotal(report.totalProducts()));
            if (report.complete()) {
                touch(jobId, job -> job.finish(Instant.now(clock)));
            } else {
                touch(jobId, job -> job.fail(
                        "Не сохранено товаров: " + report.failedProducts(), Instant.now(clock)));
            }
        } catch (RuntimeException e) {
            log.error("Фоновая задача {} не выполнена", jobId, e);
            touch(jobId, job -> job.fail(String.valueOf(e.getMessage()), Instant.now(clock)));
        }
    }

    /**
     * Тело фоновой работы. Собственной транзакции здесь нет намеренно: каждый день
     * коммитится отдельно, поэтому результат виден сразу, а прерванная загрузка
     * не откатывается целиком.
     */
    void runJob(Long jobId, String clientId, boolean syncTypes) {
        Optional<SyncJob> found = syncJobRepository.findById(jobId);
        if (found.isEmpty()) {
            log.warn("Задача {} не найдена, запуск отменён", jobId);
            return;
        }
        LocalDate from = found.get().getDateFrom();
        LocalDate to = found.get().getDateTo();

        try {
            touch(jobId, job -> job.start(Instant.now(clock)));
            if (syncTypes) {
                syncService.syncAccrualTypes();
            }
            syncService.sync(clientId, from, to, new JobProgressListener(jobId));
            touch(jobId, job -> job.finish(Instant.now(clock)));
        } catch (RuntimeException e) {
            log.error("Фоновая задача {} не выполнена", jobId, e);
            touch(jobId, job -> job.fail(String.valueOf(e.getMessage()), Instant.now(clock)));
        }
    }

    public Optional<SyncJobStatus> status(Long jobId) {
        return tx.execute(status -> syncJobRepository.findById(jobId).map(this::toStatus));
    }

    public List<SyncJobStatus> recent() {
        return tx.execute(status -> syncJobRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(this::toStatus)
                .toList());
    }

    /**
     * Активная задача того же аккаунта, пересекающаяся с запрошенным периодом.
     *
     * <p>Пересечение, а не точное совпадение: задачи за сентябрь и за конец августа
     * конфликтуют по общим дням так же, как две задачи за один период.
     */
    private Optional<SyncJob> findOverlapping(Long accountId, LocalDate from, LocalDate to) {
        return tx.execute(status -> syncJobRepository.findActiveOverlapping(accountId, from, to));
    }

    /** Обновление задачи в собственной транзакции, чтобы прогресс был виден сразу. */
    private void touch(Long jobId, Consumer<SyncJob> change) {
        tx.executeWithoutResult(status -> syncJobRepository.findById(jobId).ifPresent(job -> {
            change.accept(job);
            syncJobRepository.save(job);
        }));
    }

    private SellerAccount resolveAccount(String clientId, String marketplaceCode) {
        Marketplace marketplace = marketplaceRepository.findByCode(marketplaceCode)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Неизвестный маркетплейс: " + marketplaceCode));
        return sellerAccountRepository
                .findByMarketplaceIdAndClientId(marketplace.getId(), clientId)
                .orElseGet(() -> sellerAccountRepository.save(
                        new SellerAccount(marketplace, marketplaceCode + " " + clientId, clientId)));
    }

    private SyncJobStatus toStatus(SyncJob job) {
        return new SyncJobStatus(
                job.getId(),
                job.getMarketplaceCode(),
                job.getJobType(),
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

    private static int countDays(LocalDate from, LocalDate to) {
        return (int) (to.toEpochDay() - from.toEpochDay() + 1);
    }

    private static String describe(SyncJob job) {
        return "задача №" + job.getId() + " за период " + job.getDateFrom() + " — " + job.getDateTo();
    }

    /** Пишет прогресс задачи по мере загрузки дней. */
    private final class JobProgressListener implements SyncProgressListener {

        private final Long jobId;

        private JobProgressListener(Long jobId) {
            this.jobId = jobId;
        }

        @Override
        public void onStart(int totalDays, int daysToFetch) {
            touch(jobId, job -> job.currentDay(null));
        }

        @Override
        public void onDayStart(LocalDate day, int processedDays, int daysToFetch) {
            touch(jobId, job -> job.currentDay(day));
        }

        @Override
        public void onDayDone(LocalDate day, int processedDays, int daysToFetch) {
            touch(jobId, SyncJob::dayDone);
        }

        @Override
        public void onDayFailed(LocalDate day, RuntimeException error) {
            touch(jobId, SyncJob::dayFailed);
        }
    }

    /**
     * Пишет прогресс задачи загрузки каталога.
     *
     * <p>Счётчик дней переиспользуется как «сколько товаров обработано»: в задаче нет
     * дат, а колонки в sync_job одни. Пользователь видит «12 из 108» и не удивляется
     * отсутствию дат, потому что тип задачи виден в ответе.
     */
    private final class CatalogJobProgress implements CatalogProgressListener {

        private final Long jobId;

        private CatalogJobProgress(Long jobId) {
            this.jobId = jobId;
        }

        @Override
        public void onStart(int totalProducts) {
            touch(jobId, job -> job.setTotal(totalProducts));
        }

        @Override
        public void onProduct(int processed, int total, long sku) {
            touch(jobId, SyncJob::dayDone);
        }

        @Override
        public void onProductFailed(long sku, RuntimeException error) {
            touch(jobId, SyncJob::dayFailed);
        }
    }
}