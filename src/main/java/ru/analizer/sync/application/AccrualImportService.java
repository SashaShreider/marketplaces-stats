package ru.analizer.sync.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.account.domain.Marketplace;
import ru.analizer.account.domain.MarketplaceCredentials;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.account.repository.MarketplaceRepository;
import ru.analizer.account.repository.SellerAccountRepository;
import ru.analizer.integration.MarketplaceAdapter;
import ru.analizer.integration.model.AccrualDto;
import ru.analizer.integration.model.AccrualTypeInfo;
import ru.analizer.sync.domain.AccrualImportReport;
import ru.analizer.sync.domain.AccrualType;
import ru.analizer.sync.domain.PeriodCoverage;
import ru.analizer.sync.repository.AccrualTypeRepository;

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
 * <p>Догружаются только те дни, которых ещё нет либо которые упали: см. {@link DayStateService}.
 *
 * <p>Транзакция намеренно не охватывает весь запуск: каждый день записывается отдельно
 * (см. {@link AccrualWriter}), иначе загрузка длинного периода держала бы одну
 * гигантскую транзакцию.
 */
@Service
public class AccrualImportService {

    /** OZON не отдаёт начисления раньше этой даты. */
    private static final LocalDate EARLIEST_ACCRUAL_DATE = LocalDate.of(2022, 1, 1);

    private final MarketplaceAdapter adapter;
    private final AccrualWriter accrualWriter;
    private final DayStateService dayStateService;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final AccrualTypeRepository accrualTypeRepository;

    public AccrualImportService(MarketplaceAdapter adapter,
                       AccrualWriter accrualWriter,
                       DayStateService dayStateService,
                       MarketplaceRepository marketplaceRepository,
                       SellerAccountRepository sellerAccountRepository,
                       AccrualTypeRepository accrualTypeRepository) {
        this.adapter = adapter;
        this.accrualWriter = accrualWriter;
        this.dayStateService = dayStateService;
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.accrualTypeRepository = accrualTypeRepository;
    }

    /**
     * Загружает справочник типов начислений. Список открыт, поэтому обновляем его целиком,
     * а не зашиваем значения в код.
     *
     * <p>Справочник общий для маркетплейса, а не для пользователя: типы начислений у всех
     * продавцов OZON одинаковы. Реквизиты же берутся из аккаунта, потому что запрос идёт
     * от имени конкретного магазина.
     *
     * @param accountId аккаунт, чьими ключами выполняется запрос
     */
    @Transactional
    public int refreshAccrualTypes(Long accountId) {
        Marketplace marketplace = marketplace();
        MarketplaceCredentials credentials = requireAccount(accountId).credentials();
        Map<Integer, AccrualType> existing = new HashMap<>();
        for (AccrualType type : accrualTypeRepository.findByMarketplaceId(marketplace.getId())) {
            existing.put(type.getExternalTypeId(), type);
        }

        int saved = 0;
        for (AccrualTypeInfo remote : adapter.fetchAccrualTypes(credentials)) {
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
    public AccrualImportReport importAccruals(Long accountId, LocalDate dateFrom, LocalDate dateTo) {
        return importAccruals(accountId, dateFrom, dateTo, null);
    }

    /**
     * @param progress необязательный получатель прогресса; используется фоновой задачей,
     *                 чтобы отчёт мог показывать «12 из 30 дней»
     */
    public AccrualImportReport importAccruals(Long accountId, LocalDate dateFrom, LocalDate dateTo, ImportProgressListener progress) {
        LocalDate from = normalizeFrom(dateFrom);
        LocalDate to = normalizeTo(dateTo);
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }

        SellerAccount account = requireAccount(accountId);
        MarketplaceCredentials credentials = account.credentials();
        Map<Integer, AccrualType> types = loadTypes();

        int requestedDays = (int) (to.toEpochDay() - from.toEpochDay() + 1);

        // Считаем покрытие ДО загрузки: так понятно, сколько дней уже было в базе,
        // а сколько докачиваем сейчас. После загрузки эти числа уже не различить.
        PeriodCoverage before = dayStateService.coverage(account.getId(), from, to);
        int alreadyLoaded = before.loadedDays();

        List<LocalDate> pending = dayStateService.daysToSync(account.getId(), from, to);
        if (progress != null) {
            progress.onStart(requestedDays, pending.size());
        }
        if (pending.isEmpty()) {
            // Догружать нечего: повторный запуск не должен ходить в OZON зря.
            return new AccrualImportReport(adapter.marketplaceCode(), from, to, requestedDays,
                    0, 0, 0, 0, 0, 0, true);
        }

        Totals totals = new Totals();
        for (LocalDate date : pending) {
            if (progress != null) {
                progress.onDayStart(date, totals.processedDays(), pending.size());
            }
            try {
                dayStateService.markInProgress(account, date);
                totals.syncedDays++;

                List<AccrualDto> accruals = adapter.fetchAccrualsByDay(credentials, date);
                AccrualWriter.DayCounts counts = accruals.isEmpty()
                        ? new AccrualWriter.DayCounts(0, 0, 0)
                        : accrualWriter.persistDay(account, types, accruals);

                BigDecimal dayTotal = accruals.stream()
                        .map(AccrualDto::totalAmount)
                        .map(v -> v == null ? BigDecimal.ZERO : v)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                dayStateService.markLoaded(account, date, dayTotal, accruals.size());

                totals.received += accruals.size();
                totals.inserted += counts.inserted();
                totals.updated += counts.updated();
                totals.skipped += counts.skipped();
                if (progress != null) {
                    progress.onDayDone(date, totals.processedDays(), pending.size());
                }
            } catch (RuntimeException e) {
                // День не загрузился — это должно быть видно, а не выглядеть как «нулей нет».
                dayStateService.markFailed(account, date, e.getMessage());
                totals.failedDays++;
                totals.syncedDays--;
                if (progress != null) {
                    progress.onDayFailed(date, e);
                }
            }
        }

        boolean complete = totals.failedDays == 0
                && alreadyLoaded + totals.syncedDays == requestedDays;
        return new AccrualImportReport(adapter.marketplaceCode(), from, to, requestedDays,
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
        if (dateFrom == null) {
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

    /**
     * Аккаунт по идентификатору.
     *
     * <p>Идентификатор приходит из пути запроса, а не из параметра клиента: {@code clientId}
     * — понятие OZON, у Wildberries это {@code companyId}, и выставлять его в общем API
     * значило бы зашить в контракт одно конкретное имя.
     */
    private SellerAccount requireAccount(Long accountId) {
        return sellerAccountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalStateException(
                        "Аккаунт " + accountId + " не найден"));
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