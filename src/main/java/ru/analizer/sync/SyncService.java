package ru.analizer.sync;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.marketplace.AccrualDto;
import ru.analizer.marketplace.MarketplaceAdapter;
import ru.analizer.persistence.entity.AccrualType;
import ru.analizer.persistence.entity.ContainerFee;
import ru.analizer.persistence.entity.DeliveryService;
import ru.analizer.persistence.entity.FinanceAccrual;
import ru.analizer.persistence.entity.ItemFee;
import ru.analizer.persistence.entity.ItemFeeDetail;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.NonItemFee;
import ru.analizer.persistence.entity.Posting;
import ru.analizer.persistence.entity.PostingProduct;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.AccrualTypeRepository;
import ru.analizer.persistence.repository.ContainerFeeRepository;
import ru.analizer.persistence.repository.FinanceAccrualRepository;
import ru.analizer.persistence.repository.ItemFeeRepository;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.NonItemFeeRepository;
import ru.analizer.persistence.repository.PostingProductRepository;
import ru.analizer.persistence.repository.PostingRepository;
import ru.analizer.persistence.repository.SellerAccountRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Синхронизация финансовых данных маркетплейса в базу.
 *
 * <p>Идемпотентность обеспечивается уникальным ключом {@code (seller_account_id, external_id)},
 * где {@code external_id} — {@code accrual_id} из OZON. Повторный запуск за тот же период
 * не создаёт дубликатов, а обновляет уже сохранённые операции: OZON уточняет начисления
 * после первой выгрузки.
 *
 * <p>Детализация операции при обновлении пересобирается целиком. Это гарантирует, что в базе
 * не останется строк, порождённых прежней (возможно, ошибочной) интерпретацией ответа API.
 */
@Service
public class SyncService {

    /** OZON не отдаёт начисления раньше этой даты. */
    private static final LocalDate EARLIEST_ACCRUAL_DATE = LocalDate.of(2022, 1, 1);

    private final MarketplaceAdapter adapter;
    private final MarketplaceRepository marketplaceRepository;
    private final SellerAccountRepository sellerAccountRepository;
    private final AccrualTypeRepository accrualTypeRepository;
    private final FinanceAccrualRepository financeAccrualRepository;
    private final PostingRepository postingRepository;
    private final PostingProductRepository postingProductRepository;
    private final ItemFeeRepository itemFeeRepository;
    private final NonItemFeeRepository nonItemFeeRepository;
    private final ContainerFeeRepository containerFeeRepository;

    public SyncService(MarketplaceAdapter adapter,
                       MarketplaceRepository marketplaceRepository,
                       SellerAccountRepository sellerAccountRepository,
                       AccrualTypeRepository accrualTypeRepository,
                       FinanceAccrualRepository financeAccrualRepository,
                       PostingRepository postingRepository,
                       PostingProductRepository postingProductRepository,
                       ItemFeeRepository itemFeeRepository,
                       NonItemFeeRepository nonItemFeeRepository,
                       ContainerFeeRepository containerFeeRepository) {
        this.adapter = adapter;
        this.marketplaceRepository = marketplaceRepository;
        this.sellerAccountRepository = sellerAccountRepository;
        this.accrualTypeRepository = accrualTypeRepository;
        this.financeAccrualRepository = financeAccrualRepository;
        this.postingRepository = postingRepository;
        this.postingProductRepository = postingProductRepository;
        this.itemFeeRepository = itemFeeRepository;
        this.nonItemFeeRepository = nonItemFeeRepository;
        this.containerFeeRepository = containerFeeRepository;
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
        for (ru.analizer.marketplace.ozon.dto.AccrualType remote : adapter.fetchAccrualTypes()) {
            if (remote.id() == null) {
                continue;
            }
            String name = remote.name() == null || remote.name().isBlank()
                    ? "type_" + remote.id() : remote.name();
            AccrualType current = existing.get(remote.id());
            if (current == null) {
                accrualTypeRepository.save(new AccrualType(marketplace, remote.id(), name, remote.description()));
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

        Totals totals = new Totals();
        int days = 0;
        for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
            days++;
            List<AccrualDto> accruals = adapter.fetchAccrualsByDay(date);
            if (accruals.isEmpty()) {
                continue;
            }
            persistDay(clientId, date, accruals, totals);
        }

        return new SyncReport(adapter.marketplaceCode(), dateFrom, dateTo, days,
                totals.received, totals.inserted, totals.updated, totals.skipped, 0);
    }

    private static final class Totals {
        int received;
        int inserted;
        int updated;
        int skipped;
    }

    @Transactional
    protected void persistDay(String clientId, LocalDate date, List<AccrualDto> accruals, Totals totals) {
        SellerAccount account = resolveAccount(clientId);
        Map<Integer, AccrualType> types = loadTypes();
        totals.received += accruals.size();

        for (AccrualDto dto : accruals) {
            if (dto.externalId() == null) {
                totals.skipped++;
                continue;
            }
            Optional<FinanceAccrual> existing = financeAccrualRepository
                    .findBySellerAccountIdAndExternalId(account.getId(), dto.externalId());

            if (existing.isPresent()) {
                FinanceAccrual accrual = existing.get();
                accrual.refreshFrom(dto.date(), dto.unitNumber(), dto.category().name(),
                        dto.typeId(), dto.totalAmount(), dto.currency(), dto.rawJson());
                if (dto.typeId() != null) {
                    accrual.linkAccrualType(types.get(dto.typeId()));
                }
                clearChildren(accrual);
                createChildren(accrual, dto);
                totals.updated++;
            } else {
                FinanceAccrual accrual = new FinanceAccrual(
                        account, dto.externalId(), dto.date(), dto.unitNumber(),
                        dto.category().name(), dto.typeId(), dto.totalAmount(),
                        dto.currency(), dto.rawJson());
                if (dto.typeId() != null) {
                    accrual.linkAccrualType(types.get(dto.typeId()));
                }
                financeAccrualRepository.save(accrual);
                createChildren(accrual, dto);
                totals.inserted++;
            }
        }
    }

    private void createChildren(FinanceAccrual accrual, AccrualDto dto) {
        if (dto.posting() != null) {
            createPosting(accrual, dto.posting());
        }
        if (dto.itemFees() != null) {
            for (AccrualDto.ItemFee source : dto.itemFees()) {
                ItemFee entity = new ItemFee(accrual, source.sku());
                entity.setQuantity(source.quantity());
                for (AccrualDto.FeeDetail detail : source.fees()) {
                    entity.addDetail(new ItemFeeDetail(detail.typeId(), detail.amount(), detail.currency()));
                }
                itemFeeRepository.save(entity);
            }
        }
        if (dto.nonItemFee() != null) {
            nonItemFeeRepository.save(new NonItemFee(
                    accrual, dto.nonItemFee().typeId(), dto.nonItemFee().amount(), dto.nonItemFee().currency()));
        }
        if (dto.containerFees() != null) {
            for (AccrualDto.FeeDetail fee : dto.containerFees()) {
                containerFeeRepository.save(new ContainerFee(accrual, fee.typeId(), fee.amount(), fee.currency()));
            }
        }
    }

    private void createPosting(FinanceAccrual accrual, AccrualDto.Posting source) {
        Posting posting = postingRepository.save(
                new Posting(accrual, source.deliverySchema(), source.deliverySpeed()));

        for (AccrualDto.Product product : source.products()) {
            String currency = product.commission() != null ? product.commission().currency() : "RUB";
            PostingProduct entity = new PostingProduct(posting, product.sku(), currency);
            entity.setQuantity(product.quantity());
            if (product.commission() != null) {
                AccrualDto.Commission c = product.commission();
                entity.setCommissionValues(
                        c.sellerPrice(), c.salePrice(), c.saleAmount(), c.saleCommission(),
                        c.commission(), c.commissionRatio(), c.coinvestment(), c.bonus());
            }
            for (AccrualDto.FeeDetail service : product.deliveryServices()) {
                entity.addDeliveryService(new DeliveryService(service.typeId(), service.amount(), service.currency()));
            }
            postingProductRepository.save(entity);
        }
    }

    private void clearChildren(FinanceAccrual accrual) {
        postingRepository.findByFinanceAccrualId(accrual.getId()).ifPresent(posting -> {
            postingProductRepository.deleteByPostingId(posting.getId());
            postingRepository.delete(posting);
        });
        itemFeeRepository.deleteByFinanceAccrualId(accrual.getId());
        nonItemFeeRepository.deleteByFinanceAccrualId(accrual.getId());
        containerFeeRepository.deleteByFinanceAccrualId(accrual.getId());
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
