package ru.analizer.sync.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.analizer.integration.model.AccrualDto;
import ru.analizer.sync.domain.AccrualType;
import ru.analizer.sync.infrastructure.entity.ContainerFee;
import ru.analizer.sync.infrastructure.entity.DeliveryService;
import ru.analizer.sync.infrastructure.entity.FinanceAccrual;
import ru.analizer.sync.infrastructure.entity.ItemFee;
import ru.analizer.sync.infrastructure.entity.ItemFeeDetail;
import ru.analizer.sync.infrastructure.entity.NonItemFee;
import ru.analizer.sync.infrastructure.entity.Posting;
import ru.analizer.sync.infrastructure.entity.PostingProduct;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.sync.repository.AccrualTypeRepository;
import ru.analizer.sync.infrastructure.repository.ContainerFeeRepository;
import ru.analizer.sync.infrastructure.repository.FinanceAccrualRepository;
import ru.analizer.sync.infrastructure.repository.ItemFeeRepository;
import ru.analizer.sync.infrastructure.repository.NonItemFeeRepository;
import ru.analizer.sync.infrastructure.repository.PostingProductRepository;
import ru.analizer.sync.infrastructure.repository.PostingRepository;

import java.util.Map;
import java.util.Optional;

/**
 * Запись начислений одного дня в базу — в одной транзакции.
 *
 * <p>Отдельный компонент, а не метод {@code AccrualImportService}: транзакция должна применяться
 * через прокси Spring. Вызов метода того же класса обходит прокси, и
 * {@code @Transactional} молча не срабатывал — это проявлялось только на повторной
 * синхронизации, где выполняются удаления.
 */
@Component
public class AccrualWriter {

    private final FinanceAccrualRepository financeAccrualRepository;
    private final PostingRepository postingRepository;
    private final PostingProductRepository postingProductRepository;
    private final ItemFeeRepository itemFeeRepository;
    private final NonItemFeeRepository nonItemFeeRepository;
    private final ContainerFeeRepository containerFeeRepository;
    private final AccrualTypeRepository accrualTypeRepository;

    public AccrualWriter(FinanceAccrualRepository financeAccrualRepository,
                         PostingRepository postingRepository,
                         PostingProductRepository postingProductRepository,
                         ItemFeeRepository itemFeeRepository,
                         NonItemFeeRepository nonItemFeeRepository,
                         ContainerFeeRepository containerFeeRepository,
                         AccrualTypeRepository accrualTypeRepository) {
        this.financeAccrualRepository = financeAccrualRepository;
        this.postingRepository = postingRepository;
        this.postingProductRepository = postingProductRepository;
        this.itemFeeRepository = itemFeeRepository;
        this.nonItemFeeRepository = nonItemFeeRepository;
        this.containerFeeRepository = containerFeeRepository;
        this.accrualTypeRepository = accrualTypeRepository;
    }

    @Transactional
    public DayCounts persistDay(SellerAccount account,
                                Map<Integer, AccrualType> types,
                                java.util.List<AccrualDto> accruals) {
        int inserted = 0;
        int updated = 0;
        int skipped = 0;

        for (AccrualDto dto : accruals) {
            if (dto.externalId() == null) {
                // Без accrual_id операцию нельзя идентифицировать, повторный запуск
                // задублировал бы её. Исходный JSON при этом остаётся в ответе API.
                skipped++;
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
                updated++;
            } else {                FinanceAccrual accrual = new FinanceAccrual(
                        account, dto.externalId(), dto.date(), dto.unitNumber(),
                        dto.category().name(), dto.typeId(), dto.totalAmount(),
                        dto.currency(), dto.rawJson());
                if (dto.typeId() != null) {
                    accrual.linkAccrualType(types.get(dto.typeId()));
                }
                financeAccrualRepository.save(accrual);
                createChildren(accrual, dto);
                inserted++;
            }
        }
        return new DayCounts(inserted, updated, skipped);
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

    /**
     * Детализация при обновлении пересобирается целиком, иначе в базе остались бы строки,
     * порождённые прежней интерпретацией ответа API.
     *
     * <p>После удалений нужен явный {@code flush}: Hibernate выполняет операции в порядке
     * вставки, обновления, удаления, поэтому без сброса новая строка вставилась бы раньше,
     * чем удалилась прежняя, и упала бы на уникальном индексе.
     */
    private void clearChildren(FinanceAccrual accrual) {
        postingRepository.findByFinanceAccrualId(accrual.getId()).ifPresent(posting -> {
            postingProductRepository.deleteByPostingId(posting.getId());
            postingRepository.delete(posting);
        });
        itemFeeRepository.deleteByFinanceAccrualId(accrual.getId());
        nonItemFeeRepository.deleteByFinanceAccrualId(accrual.getId());
        containerFeeRepository.deleteByFinanceAccrualId(accrual.getId());
        financeAccrualRepository.flush();
    }

    /** Счётчики за один день. */
    public record DayCounts(int inserted, int updated, int skipped) {
    }
}
