package ru.analizer.marketplace.ozon;

import ru.analizer.marketplace.AccrualDto;
import ru.analizer.marketplace.ozon.dto.Delivery;
import ru.analizer.marketplace.ozon.dto.DeliveryService;
import ru.analizer.marketplace.ozon.dto.FinanceAccrual;
import ru.analizer.marketplace.ozon.dto.ItemFee;
import ru.analizer.marketplace.ozon.dto.ItemFees;
import ru.analizer.marketplace.ozon.dto.Money;
import ru.analizer.marketplace.ozon.dto.NonItemFee;
import ru.analizer.marketplace.ozon.dto.Posting;
import ru.analizer.marketplace.ozon.dto.PostingProduct;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Перевод DTO OZON в маркетплейс-независимое представление.
 * Только преобразование данных: ни запросов, ни обращения к БД.
 */
public final class OzonMapper {

    private OzonMapper() {
    }

    public static AccrualDto toAccrualDto(FinanceAccrual accrual, String rawJson) {
        return new AccrualDto(
                accrual.accrualId(),
                accrual.accruedDate(),
                accrual.unitNumber(),
                toCategory(accrual),
                accrual.typeId(),
                amount(accrual.totalAmount()),
                currency(accrual.totalAmount(), "RUB"),
                toPosting(accrual.posting()),
                toItemFees(accrual.itemFees()),
                toNonItemFee(accrual.nonItemFee()),
                toContainerFees(accrual.containerFees()),
                rawJson);
    }

    /**
     * NON_ITEM бывает только у своей категории. Возвращать «пусты��к» FeeDetail здесь нельзя:
     * вызывающий код проверяет на null, и пустой объект привёл бы к вставке строки
     * с null type_id вместо её отсутствия.
     */
    private static AccrualDto.FeeDetail toNonItemFee(NonItemFee nonItemFee) {
        return nonItemFee == null ? null : toFeeDetail(nonItemFee.typeId(), nonItemFee.accrued());
    }

    private static AccrualDto.Category toCategory(FinanceAccrual accrual) {
        return switch (accrual.category()) {
            case POSTING -> AccrualDto.Category.POSTING;
            case ITEM -> AccrualDto.Category.ITEM;
            case NON_ITEM -> AccrualDto.Category.NON_ITEM;
            case CONTAINER_FEES -> AccrualDto.Category.CONTAINER_FEES;
            case UNSPECIFIED -> AccrualDto.Category.UNSPECIFIED;
            case UNKNOWN -> AccrualDto.Category.UNKNOWN;
        };
    }

    private static AccrualDto.Posting toPosting(Posting posting) {
        if (posting == null) {
            return null;
        }
        List<AccrualDto.Product> products = new ArrayList<>();
        for (PostingProduct product : posting.safeProducts()) {
            products.add(toProduct(product));
        }
        return new AccrualDto.Posting(posting.deliverySchema(), posting.deliverySpeed(), products);
    }

    private static AccrualDto.Product toProduct(PostingProduct product) {
        Delivery delivery = product.delivery();
        List<AccrualDto.FeeDetail> services = new ArrayList<>();
        if (delivery != null) {
            for (DeliveryService service : delivery.safeServices()) {
                services.add(toFeeDetail(service.typeId(), service.accrued()));
            }
        }
        return new AccrualDto.Product(
                product.sku(),
                product.quantityOrDefault(),
                toCommission(product),
                delivery == null ? null : amount(delivery.totalAccrued()),
                delivery == null || delivery.totalAccrued() == null
                        ? null : currency(delivery.totalAccrued(), null),
                services);
    }

    private static AccrualDto.Commission toCommission(PostingProduct product) {
        if (product.commission() == null) {
            // Бывает начисление только по логистике, без комиссии.
            return null;
        }
        var c = product.commission();
        return new AccrualDto.Commission(
                amount(c.sellerPrice()),
                amount(c.salePrice()),
                amount(c.saleAmount()),
                amount(c.saleCommission()),
                amount(c.commission()),
                c.commissionRatio(),
                amount(c.coinvestment()),
                amount(c.bonus()),
                currency(c.salePrice(), "RUB"));
    }

    private static List<AccrualDto.ItemFee> toItemFees(ItemFees itemFees) {
        if (itemFees == null) {
            return null;
        }
        List<AccrualDto.ItemFee> result = new ArrayList<>();
        for (ItemFee fee : itemFees.safeFees()) {
            List<AccrualDto.FeeDetail> details = new ArrayList<>();
            for (var detail : fee.safeFees()) {
                details.add(toFeeDetail(detail.typeId(), detail.accrued()));
            }
            result.add(new AccrualDto.ItemFee(fee.sku(), fee.quantityOrDefault(), details));
        }
        return result;
    }

    private static List<AccrualDto.FeeDetail> toContainerFees(ru.analizer.marketplace.ozon.dto.ContainerFees containerFees) {
        if (containerFees == null) {
            return null;
        }
        List<AccrualDto.FeeDetail> result = new ArrayList<>();
        for (var fee : containerFees.safeFees()) {
            result.add(toFeeDetail(fee.typeId(), fee.accrued()));
        }
        return result;
    }

    private static AccrualDto.FeeDetail toFeeDetail(Integer typeId, Money money) {
        return new AccrualDto.FeeDetail(typeId, amount(money), currency(money, "RUB"));
    }

    private static BigDecimal amount(Money money) {
        return money == null ? null : money.amount();
    }

    private static String currency(Money money, String fallback) {
        if (money == null || money.currency() == null || money.currency().isBlank()) {
            return fallback;
        }
        return money.currency();
    }
}
