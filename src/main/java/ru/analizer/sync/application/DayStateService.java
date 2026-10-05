package ru.analizer.sync.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.sync.domain.DayStatus;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.sync.domain.ImportedDay;
import ru.analizer.sync.domain.PeriodCoverage;
import ru.analizer.sync.repository.ImportedDayRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Учёт того, какие дни загружены и насколько им можно доверять.
 *
 * <h2>Почему день бывает «не окончательным»</h2>
 * Начисления за текущий день продолжают приходить в течение суток, а за вчерашний — могут
 * появиться позже: возврат, корректировка, доначисление комиссии. Поэтому «загрузили день»
 * и «данные за день окончательные» — разные вещи, и путать их нельзя: иначе отчёт за период
 * с сегодняшним днём покажет заниженные цифры, не сказав об этом.
 *
 * <h2>Как определяем зрелость, а не гадаем</h2>
 * Глубину окна зрелости никто не знает заранее, поэтому система её измеряет: при каждой
 * повторной загрузке сумма дня сравнивается с предыдущей, и число расхождений копится в
 * {@code change_count}. Через пару недель работы по этим данным видно, сколько дней
 * реально нужно ждать.
 *
 * <p>TODO(#scheduler): автообновление свежего окна. Пока данные обновляются только по
 * явному запросу. Планировщик (@Scheduled) имеет смысл добавить, когда появится несколько
 * пользователей или период просмотра перестанет быть коротким.
 */
@Service
public class DayStateService {

    private static final Logger log = LoggerFactory.getLogger(DayStateService.class);

    /**
     * Окно зрелости, дней. День считается окончательным, только если он старше этого окна.
     * Значение подобрано по требованиям задачи; фактическую глубину уточняем по накопленной
     * статистике {@code change_count}.
     */
    public static final int DEFAULT_MATURITY_DAYS = 3;

    private final ImportedDayRepository syncDayRepository;
    private final Clock clock;
    private final int maturityInDays;

    public DayStateService(ImportedDayRepository syncDayRepository, ImportProperties properties, Clock clock) {
        this.syncDayRepository = syncDayRepository;
        this.clock = clock;
        this.maturityInDays = properties.maturityDays() > 0
                ? properties.maturityDays()
                : DEFAULT_MATURITY_DAYS;
    }

    public int maturityInDays() {
        return maturityInDays;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Считает состояние периода. Обращения к OZON не делает: только чтение учёта.
     */
    @Transactional(readOnly = true)
    public PeriodCoverage coverage(Long sellerAccountId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }

        if (sellerAccountId == null) {
            // Аккаунта ещё нет: ни одного дня не загружено. Это не ошибка,
            // а честный ответ на вопрос «что у тебя есть».
            return PeriodCoverage.empty(from, to);
        }

        List<ImportedDay> days = syncDayRepository.findBySellerAccountIdAndDayBetween(
                sellerAccountId, from, to);

        Set<LocalDate> loaded = new HashSet<>();
        List<LocalDate> provisional = new ArrayList<>();
        List<LocalDate> failed = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;

        for (ImportedDay day : days) {
            if (day.getStatus() == DayStatus.DONE) {
                loaded.add(day.getDay());
                if (!day.isFinalDay()) {
                    provisional.add(day.getDay());
                }
                sum = sum.add(day.getTotalAmount() == null ? BigDecimal.ZERO : day.getTotalAmount());
            } else if (day.getStatus() == DayStatus.FAILED) {
                failed.add(day.getDay());
            }
        }

        List<LocalDate> missing = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            if (!loaded.contains(date) && !failed.contains(date)) {
                missing.add(date);
            }
        }

        int requested = (int) (to.toEpochDay() - from.toEpochDay() + 1);
        int finalDays = loaded.size() - provisional.size();

        return new PeriodCoverage(requested, loaded.size(), finalDays, failed.size(),
                missing, provisional, failed, sum);
    }

    /**
     * Дни периода, которые нужно забрать из OZON.
     *
     * <p>Правило не такое простое, как «все загруженные дни пропускаем»:
     * <ul>
     *   <li>незагруженные — забрать, иначе в базе не будет данных;</li>
     *   <li>упавшие — забрать заново, ошибка могла быть временной;</li>
     *   <li><b>загруженные, но ещё не окончательные — забрать снова.</b> Начисления за
     *       свежие дни продолжают приходить (возвраты, корректировки), поэтому пропуск
     *       означал бы, что данные за вчерашний день так и останутся неполными.</li>
     * </ul>
     * Окончательные дни повторно не трогаем: они уже не меняются, а лишние запросы
     * к OZON только расходуют лимит.
     */
    @Transactional(readOnly = true)
    public List<LocalDate> daysToSync(Long sellerAccountId, LocalDate from, LocalDate to) {
        PeriodCoverage coverage = coverage(sellerAccountId, from, to);
        List<LocalDate> result = new ArrayList<>(coverage.missingDays());
        result.addAll(coverage.failedDates());
        // Дни внутри окна зрелости перезапрашиваются: их сумма ещё может измениться.
        result.addAll(coverage.provisionalDays());
        result.sort(LocalDate::compareTo);
        return result;
    }

    /**
     * Отмечает начало загрузки дня.
     */
    @Transactional
    public ImportedDay markInProgress(SellerAccount account, LocalDate day) {
        ImportedDay record = findOrCreate(account, day);
        record.markInProgress();
        return syncDayRepository.save(record);
    }

    /**
     * Фиксирует успешную загрузку дня.
     *
     * @return {@code true}, если сумма дня изменилась относительно прошлой загрузки
     */
    @Transactional
    public boolean markLoaded(SellerAccount account, LocalDate day, BigDecimal totalAmount, int accrualCount) {
        Instant now = Instant.now(clock);
        ImportedDay record = findOrCreate(account, day);
        boolean changed = record.recordSyncResult(totalAmount, accrualCount, now);
        record.refreshFinality(today(), maturityInDays,
                record.dataProvenStable(maturityInDays, today()));
        syncDayRepository.save(record);

        if (changed) {
            log.info("Данные за {} изменились при повторной загрузке: сумма теперь {}, начислений {}",
                    day, totalAmount, accrualCount);
        }
        return changed;
    }

    @Transactional
    public void markFailed(SellerAccount account, LocalDate day, String error) {
        ImportedDay record = findOrCreate(account, day);
        record.markFailed(error, Instant.now(clock));
        syncDayRepository.save(record);
        log.warn("Не удалось загрузить данные за {}: {}", day, error);
    }

    private ImportedDay findOrCreate(SellerAccount account, LocalDate day) {
        Optional<ImportedDay> existing = syncDayRepository.findBySellerAccountIdAndDay(account.getId(), day);
        return existing.orElseGet(() -> new ImportedDay(account, day));
    }
}