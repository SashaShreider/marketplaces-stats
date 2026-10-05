package ru.analizer.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.analizer.catalog.CatalogImportProgressListener;
import ru.analizer.catalog.CatalogImportReport;
import ru.analizer.catalog.CatalogImportService;
import ru.analizer.persistence.entity.ImportRun;
import ru.analizer.persistence.entity.ImportType;
import ru.analizer.persistence.entity.RunState;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.ImportRunRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Фоновые импорты данных маркетплейса.
 *
 * <p>Зачем они нужны: импорт месяца — это десятки обращений к OZON, а года — сотни.
 * Если делать это внутри HTTP-запроса, клиент отвалится по таймауту, хотя данные
 * наполовину загрузятся. Поэтому запрос создаёт прогон и сразу возвращает его номер,
 * а работа уходит в отдельный поток.
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
 * <p>Два импорта, пересекающиеся по датам, запустить нельзя: они писали бы одни и те же
 * операции. Проверка и создание прогона выполняются под одним монитором, поэтому два
 * одновременных POST-запроса не пройдут оба.
 *
 * <p>TODO(#multi-instance): защита от гонки опирается на память одного процесса. При
 * нескольких экземплярах приложения блокировку нужно перенести в базу — например,
 * частичным уникальным индексом на активные прогоны.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    private final ImportRunRepository importRunRepository;
    private final AccrualImportService accrualImportService;
    private final CatalogImportService catalogImportService;
    private final TaskExecutor taskExecutor;
    private final TransactionTemplate tx;
    private final Clock clock;

    /**
     * Монитор создания прогонов: защищает проверку конфликта и вставку строки от гонки.
     */
    private final Object submitLock = new Object();

    public ImportService(ImportRunRepository importRunRepository,
                         AccrualImportService accrualImportService,
                         CatalogImportService catalogImportService,
                         @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
                         PlatformTransactionManager transactionManager,
                         Clock clock) {
        this.importRunRepository = importRunRepository;
        this.accrualImportService = accrualImportService;
        this.catalogImportService = catalogImportService;
        this.taskExecutor = taskExecutor;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    /**
     * Запускает импорт начислений за период и сразу возвращает прогресс.
     *
     * <p>Догружаются только недостающие дни: повторный запуск не создаёт дублей
     * и не ходит в OZON зря.
     *
     * @param refreshAccrualTypes обновить ли справочник типов начислений перед загрузкой
     * @throws ImportConflictException если уже идёт импорт пересекающегося периода
     */
    public ImportProgress startFinanceImport(SellerAccount account, String marketplaceCode,
                                             LocalDate dateFrom, LocalDate dateTo,
                                             boolean refreshAccrualTypes) {
        if (dateFrom == null || dateTo == null) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }

        Long importId;
        Long accountId = account.getId();
        synchronized (submitLock) {
            Optional<ImportRun> active = findOverlapping(accountId, dateFrom, dateTo);
            if (active.isPresent()) {
                throw ImportConflictException.overlappingPeriod(
                        active.get().getId(), describe(active.get()), dateFrom, dateTo);
            }

            ImportRun run = tx.execute(status -> importRunRepository.save(new ImportRun(
                    account, marketplaceCode, ImportType.FINANCE,
                    dateFrom, dateTo, countDays(dateFrom, dateTo))));
            if (run == null) {
                throw new IllegalStateException("Не удалось создать прогон импорта");
            }
            importId = run.getId();
        }

        // Запуск после выхода из монитора: иначе фоновая работа могла бы начаться
        // и занять его раньше, чем проверка завершится.
        taskExecutor.execute(() -> runFinanceImport(importId, accountId, refreshAccrualTypes));

        return progressOf(importId);
    }

    /**
     * Запускает импорт каталога товаров и сразу возвращает прогресс.
     *
     * <p>Даты не задаются и не проверяются: у каталога их нет. Конфликт проще —
     * достаточно, чтобы активный импорт каталога был один, иначе два процесса
     * переписывали бы одни и те же товары.
     */
    public ImportProgress startCatalogImport(SellerAccount account, String marketplaceCode) {
        Long importId;
        Long accountId = account.getId();
        synchronized (submitLock) {
            Optional<ImportRun> active = tx.execute(status ->
                    importRunRepository.findFirstActiveOfType(accountId, ImportType.CATALOG));
            if (active.isPresent()) {
                throw ImportConflictException.catalogBusy(active.get().getId());
            }

            ImportRun run = tx.execute(status -> importRunRepository.save(new ImportRun(
                    account, marketplaceCode, ImportType.CATALOG, null, null, 0)));
            if (run == null) {
                throw new IllegalStateException("Не удалось создать прогон импорта каталога");
            }
            importId = run.getId();
        }

        taskExecutor.execute(() -> runCatalogImport(importId, accountId));

        return progressOf(importId);
    }

    /**
     * Прогресс одного прогона в границах аккаунта.
     *
     * <p>Проверка принадлежности аккаунту обязательна: иначе зная номер прогона,
     * можно было бы читать прогресс чужого маркетплейса.
     */
    public Optional<ImportProgress> progressOf(Long importId, Long accountId) {
        return tx.execute(status -> importRunRepository
                .findByIdAndSellerAccountId(importId, accountId)
                .map(this::toProgress));
    }

    /** Последние прогоны аккаунта, новые сверху. */
    public List<ImportProgress> recentFor(Long accountId) {
        return tx.execute(status -> importRunRepository
                .findTop20BySellerAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::toProgress)
                .toList());
    }
    /** Прогресс только что созданного прогона: он уже сохранён, поэтому отсутствие невозможно. */
    private ImportProgress progressOf(Long importId) {
        return progress(importId)
                .orElseThrow(() -> new IllegalStateException(
                        "Прогон " + importId + " не найден сразу после создания"));
    }

    /** Прогресс прогона по номеру, без проверки принадлежности аккаунту. */
    private Optional<ImportProgress> progress(Long importId) {
        return tx.execute(status -> importRunRepository.findById(importId).map(this::toProgress));
    }

    /**
     * Тело фонового импорта начислений.
     *
     * <p>Собственной транзакции здесь нет намеренно: каждый день коммитится отдельно,
     * поэтому результат виден сразу, а прерванный импорт не откатывается целиком.
     */
    void runFinanceImport(Long importId, Long accountId, boolean refreshAccrualTypes) {
        Optional<ImportRun> found = importRunRepository.findById(importId);
        if (found.isEmpty()) {
            log.warn("Прогон {} не найден, импорт отменён", importId);
            return;
        }
        LocalDate from = found.get().getDateFrom();
        LocalDate to = found.get().getDateTo();

        try {
            touch(importId, run -> run.start(Instant.now(clock)));
            if (refreshAccrualTypes) {
                accrualImportService.refreshAccrualTypes();
            }
            accrualImportService.importAccruals(
                    accountId, from, to, new FinanceImportProgress(importId));
            touch(importId, run -> run.finish(Instant.now(clock)));
        } catch (RuntimeException e) {
            log.error("Фоновая работа прогона {} не выполнена", importId, e);
            touch(importId, run -> run.fail(String.valueOf(e.getMessage()), Instant.now(clock)));
        }
    }

    /** Тело фонового импорта каталога. */
    void runCatalogImport(Long importId, Long accountId) {
        if (importRunRepository.findById(importId).isEmpty()) {
            log.warn("Прогон {} не найден, импорт каталога отменён", importId);
            return;
        }
        try {
            touch(importId, run -> run.start(Instant.now(clock)));
            CatalogImportReport report =
                    catalogImportService.importProducts(accountId, new CatalogImportProgress(importId));
            touch(importId, run -> run.setTotalUnits(report.totalProducts()));
            if (report.complete()) {
                touch(importId, run -> run.finish(Instant.now(clock)));
            } else {
                touch(importId, run -> run.fail(
                        "Не сохранено товаров: " + report.failedProducts(), Instant.now(clock)));
            }
        } catch (RuntimeException e) {
            log.error("Фоновая работа прогона {} не выполнена", importId, e);
            touch(importId, run -> run.fail(String.valueOf(e.getMessage()), Instant.now(clock)));
        }
    }

    /**
     * Активный прогон того же аккаунта, период которого пересекается с запрошенным.
     *
     * <p>Пересечение, а не точное совпадение: прогоны за сентябрь и за конец августа
     * конфликтуют по общим дням так же, как два прогона за один период.
     */
    private Optional<ImportRun> findOverlapping(Long accountId, LocalDate from, LocalDate to) {
        return tx.execute(status -> importRunRepository.findActiveOverlapping(accountId, from, to));
    }

    /** Обновление прогона в собственной транзакции, чтобы прогресс был виден сразу. */
    private void touch(Long importId, Consumer<ImportRun> change) {
        tx.executeWithoutResult(status -> importRunRepository.findById(importId).ifPresent(run -> {
            change.accept(run);
            importRunRepository.save(run);
        }));
    }

    private static int countDays(LocalDate from, LocalDate to) {
        return (int) (to.toEpochDay() - from.toEpochDay() + 1);
    }

    private static String describe(ImportRun run) {
        return "прогон №" + run.getId() + " за период "
                + run.getDateFrom() + " — " + run.getDateTo();
    }

private ImportProgress toProgress(ImportRun run) {
        return new ImportProgress(
                run.getId(),
                run.getMarketplaceCode(),
                run.getImportType(),
                run.getDateFrom(),
                run.getDateTo(),
                run.getStatus(),
                run.getTotalUnits(),
                run.getDoneUnits(),
                run.getSkippedUnits(),
                run.getFailedUnits(),
                run.getCurrentUnit(),
                run.getError(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getStatus() == RunState.RUNNING || run.getStatus() == RunState.PENDING);
    }

    /** Пишет прогресс прогона по мере загрузки дней. */
    private final class FinanceImportProgress implements ImportProgressListener {

        private final Long importId;

        private FinanceImportProgress(Long importId) {
            this.importId = importId;
        }

        @Override
        public void onStart(int totalDays, int daysToFetch) {
            touch(importId, run -> run.currentUnit(null));
        }

        @Override
        public void onDayStart(LocalDate day, int processedDays, int daysToFetch) {
            touch(importId, run -> run.currentUnit(day.toString()));
        }

        @Override
        public void onDayDone(LocalDate day, int processedDays, int daysToFetch) {
            touch(importId, ImportRun::unitDone);
        }

        @Override
        public void onDayFailed(LocalDate day, RuntimeException error) {
            touch(importId, ImportRun::unitFailed);
        }
    }

    /**
     * Пишет прогресс прогона импорта каталога.
     *
     * <p>Счётчик дней переиспользуется как «сколько единиц обработано»: у каталога нет
     * дат, а колонка в {@code import_run} одна. Пользователь видит «12 из 108» и не
     * удивляется отсутствию дат, потому что тип прогона виден в ответе.
     */
    private final class CatalogImportProgress implements CatalogImportProgressListener {

        private final Long importId;

        private CatalogImportProgress(Long importId) {
            this.importId = importId;
        }

        @Override
        public void onStart(int totalProducts) {
            touch(importId, run -> run.setTotalUnits(totalProducts));
        }

        @Override
        public void onProduct(int processed, int total, long sku) {
            touch(importId, ImportRun::unitDone);
        }

        @Override
        public void onProductFailed(long sku, RuntimeException error) {
            touch(importId, ImportRun::unitFailed);
        }
    }
}