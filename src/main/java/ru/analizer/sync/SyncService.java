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
 * <p>Транзакция намеренно не охватывает весь запуск: каждый день записывается отдельно
 * (см. {@link AccrualWriter}), иначе синхронизация длинного периода держала бы одну
 * гигантскую транзакцию.
 */
@Service
public class SyncService {

    /** OZON не отдаёт начисления раньше этой даты. */
    private static final LocalDate EARLIEST_ACCRUAL_DATE = LocalDate.of(2022, 1, 1);

    private final MarketplaceAdapter adapter;
    private final AccrualWriter accrualWriter;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final AccrualTypeRepository accrualTypeRepository;

    public SyncService(MarketplaceAdapter adapter,
                       AccrualWriter accrualWriter,
                       MarketplaceRepository marketplaceRepository,
                       SellerAccountRepository sellerAccountRepository,
                       AccrualTypeRepository accrualTypeRepository) {
        this.adapter = adapter;
        this.accrualWriter = accrualWriter;
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

    public SyncReport sync(String clientId, LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom == null || dateTo == null) {
            throw new IllegalArgumentException("dateFrom и dateTo обязательны");
        }
        if (dateTo.isBefore(dateFrom)) {
            throw new IllegalArgumentException("dateTo не может быть раньше dateFrom");
        }
        if (dateFrom.isBefore(EARLIEST_ACCRUAL_DATE)) {
            dateFrom = EARLIEST_ACCRUAL_DATE;
        }

        SellerAccount account = resolveAccount(clientId);
        Map<Integer, AccrualType> types = loadTypes();

        int received = 0;
        int inserted = 0;
        int updated = 0;
        int skipped = 0;
        int days = 0;

        for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
            days++;
            List<AccrualDto> accruals = adapter.fetchAccrualsByDay(date);
            if (accruals.isEmpty()) {
                continue;
            }
            received += accruals.size();
            AccrualWriter.DayCounts counts = accrualWriter.persistDay(account, types, accruals);
            inserted += counts.inserted();
            updated += counts.updated();
            skipped += counts.skipped();
        }

        return new SyncReport(adapter.marketplaceCode(), dateFrom, dateTo, days,
                received, inserted, updated, skipped, 0);
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
