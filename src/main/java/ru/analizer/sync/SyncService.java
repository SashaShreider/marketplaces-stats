package ru.analizer.sync;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.marketplace.AccrualDto;
import ru.analizer.marketplace.AccrualTypeInfo;
import ru.analizer.marketplace.MarketplaceAdapter;
import ru.analizer.persistence.entity.AccrualType;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.AccrualTypeRepository;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Синхронизация финансовых данных маркетплейса в базу.
 *
 * <p>Идемпотентность обеспечивается уникальным ключом {@code (seller_account_id, external_id)},
 * где {@code external_id} — {@code accrual_id} маркетплейса. Повторный запуск за тот же
 * период не создаёт дубликатов, а обновляет уже сохранённые операции: OZON уточняет
 * начисления после первой выгрузки.
 *
 * <p>Догружаются только те дни, которых ещё нет либо которые упали: см. {@link SyncDayService}.
 *
 * <p>Транзакция намеренно не охватывает весь запуск: каждый день записывается отдельно
 * (см. {@link AccrualWriter}), иначе загрузка длинного периода держала бы одну
 * гигантскую транзакцию.
 */
@Service
public class SyncService {

    /** OZON не отдаёт начисления раньше этой даты. */
    private static final LocalDate EARLIEST_ACCRUAL_DATE = LocalDate.of(2022, 1, 1);

    private final MarketplaceAdapter adapter;
    private final AccrualWriter accrualWriter;
    private final SyncDayService syncDayService;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final AccrualTypeRepository accrualTypeRepository;

    public SyncService(MarketplaceAdapter adapter,
                       AccrualWriter accrualWriter,
                       SyncDayService syncDayService,
                       MarketplaceRepository marketplaceRepository,
                       SellerAccountRepository sellerAccountRepository,
                       AccrualTypeRepository accrualTypeRepository) {
        this.adapter = adapter;
        this.accrualWriter = accrualWriter;
        this.syncDayService = syncDayService;
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.accrualTypeRepository = accrualTypeRepository;
    }

    /**
     * Загружает справочник типов начислений. Список открыт, поэтому обновляем его целиком,
     * а не зашиваем значения в код.
     */
    @Transactional
    public int syncAccrualTypes() {
        Marketplace marketplace = marketplace();
        Map<Integer, AccrualType> existing = new HashMap<>();
        for (AccrualType type : accrualTypeRepository.findByMarketplaceId(marketplace.getId())) {
            existing.put(type.getExternalTypeId(), type);
        }

        int saved = 0;
        for (AccrualTypeInfo remote : adapter.fetchAccrualTypes()) {
            if (remote.externalId() == null) {
                continue;
            }
            String name = remote.name() == null || remote.name().isBlank()
                    ? "type_" + remote.externalId() : remote.name();
            AccrualType current = existing.get(remote.externalId());
            if (current == null) {
                accrualTypeRepository.save(
                        new AccrualType(marketplace, remote.externalId(), name, remote.description()));
            } else {
                current.updateFrom(name, remote.description());
            }
            saved++;
        }
        return saved;
    }

    /**
     * Загружает период, докачивая только недостающие дни.
     */
    public SyncReport sync(String clientId, LocalDate dateFrom, LocalDate dateTo) {
        return sync(clientId, dateFrom, dateTo, null);
    }

    /**
     * @param progress необязательный получатель прогресса; используется фоновой задачей,
     *                 чтобы отчёт мог показывать «12 из 30 дней»
     */
    public SyncReport sync(String clientId, LocalDate dateFrom, LocalDate dateTo, SyncProgressListener progress) {
        LocalDate from = normalizeFrom(dateFrom);
        LocalDate to = normalizeTo(dateTo);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }

        SellerAccount account = resolveAccount(clientId);
        Map<Integer, AccrualType> types = loadTypes();

        int requestedDays = (int) (to.toEpochDay() - from.toEpochDay() + 1);

        // Считаем покрытие ДО загрузки: так понятно, сколько дней уже было в базе,
        // а сколько докачиваем сейчас. После загрузки эти числа уже не различить.
        SyncCoverage before = syncDayService.coverage(account.getId(), from, to);
        int alreadyLoaded = before.loadedDays();

        List<LocalDate> pending = syncDayService.daysToSync(account.getId(), from, to);
        if (progress != null) {
            progress.onStart(requestedDays, pending.size());
        }
        if (pending.isEmpty()) {
            // Догружать нечего: повторный запуск не должен ходить в OZON зря.
            return new SyncReport(adapter.marketplaceCode(), from, to, requestedDays,
                    0, 0, 0, 0, 0, 0, true);
        }

        Totals totals = new Totals();
        for (LocalDate date : pending) {
            if (progress != null) {
                progress.onDayStart(date, totals.processedDays(), pending.size());
            }
            try {
                syncDayService.markInProgress(account, date);
                totals.syncedDays++;

                List<AccrualDto> accruals = adapter.fetchAccrualsByDay(date);
                AccrualWriter.DayCounts counts = accruals.isEmpty()
                        ? new AccrualWriter.DayCounts(0, 0, 0)
                        : accrualWriter.persistDay(account, types, accruals);

                BigDecimal dayTotal = accruals.stream()
                        .map(AccrualDto::totalAmount)
                        .map(v -> v == null ? BigDecimal.ZERO : v)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                syncDayService.markLoaded(account, date, dayTotal, accruals.size());

                totals.received += accruals.size();
                totals.inserted += counts.inserted();
                totals.updated += counts.updated();
                totals.skipped += counts.skipped();
            } catch (RuntimeException e) {
                // День не загрузился — это должно быть видно, а не выглядеть как «нулей нет».
                syncDayService.markFailed(account, date, e.getMessage());
                totals.failedDays++;
                totals.syncedDays--;
                if (progress != null) {
                    progress.onDayFailed(date, e);
                }
            }
        }

        boolean complete = totals.failedDays == 0
                && alreadyLoaded + totals.syncedDays == requestedDays;
        return new SyncReport(adapter.marketplaceCode(), from, to, requestedDays,
                totals.received, totals.inserted, totals.updated, totals.skipped, 0,
                totals.syncedDays, complete);
    }

    private static final class Totals {
        int received;
        int inserted;
        int updated;
        int skipped;
        int syncedDays;
        int failedDays;

        int processedDays() {
            return syncedDays + failedDays;
        }
    }

    private LocalDate normalizeFrom(LocalDate dateFrom) {
        if (dateFrom == null || dateToIsNull(dateFrom)) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        return dateFrom.isBefore(EARLIEST_ACCRUAL_DATE) ? EARLIEST_ACCRUAL_DATE : dateFrom;
    }

    private static LocalDate normalizeTo(LocalDate dateTo) {
        if (dateToIsNull(dateTo)) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        return dateTo;
    }

    private static boolean dateToIsNull(LocalDate date) {
        return date == null;
    }

    private Marketplace marketplace() {
        return marketplaceRepository.findByCode(adapter.marketplaceCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Маркетплейс " + adapter.marketplaceCode() + " не найден в таблице marketplace"));
    }

    private SellerAccount resolveAccount(String clientId) {
        Marketplace marketplace = marketplace();
        return sellerAccountRepository
                .findByMarketplaceIdAndClientId(marketplace.getId(), clientId)
                .orElseGet(() -> sellerAccountRepository.save(
                        new SellerAccount(marketplace, adapter.marketplaceCode() + " " + clientId, clientId)));
    }

    private Map<Integer, AccrualType> loadTypes() {
        Marketplace marketplace = marketplace();
        Map<Integer, AccrualType> types = new HashMap<>();
        for (AccrualType type : accrualTypeRepository.findByMarketplaceId(marketplace.getId())) {
            types.put(type.getExternalTypeId(), type);
        }
        return types;
    }
}