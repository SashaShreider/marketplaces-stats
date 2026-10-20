package ru.analizer.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.catalog.domain.OzonProduct;

import java.util.List;
import java.util.Optional;

public interface OzonProductRepository extends JpaRepository<OzonProduct, Long> {

    Optional<OzonProduct> findBySellerAccountIdAndSku(Long sellerAccountId, long sku);

    long countBySellerAccountId(Long sellerAccountId);

    /**
     * SKU из начислений, которых нет в каталоге.
     *
     * <p>Период намеренно не ограничен: обход каталога тоже не привязан к датам, и
     * иначе архивный товар продажи двухлетней давности не догружался бы никогда, хотя
     * его расходы до сих пор сидят в отчёте.
     *
     * <p>Берём и начисления, и списания по SKU: у товара может не быть продаж, но
     * быть штраф, и такой товар тоже должен попасть в каталог.
     */
    @Query(value = """
            select distinct s.sku
            from (
                select p.sku as sku
                from finance_accrual a
                join posting po on po.finance_accrual_id = a.id
                join posting_product p on p.posting_id = po.id
                where a.seller_account_id = :accountId
                union
                select f.sku as sku
                from finance_accrual a
                join item_fee f on f.finance_accrual_id = a.id
                where a.seller_account_id = :accountId
            ) s
            where not exists (
                select 1 from ozon_product op
                where op.seller_account_id = :accountId and op.sku = s.sku
            )
            order by s.sku
            """, nativeQuery = true)
    List<Long> skusMissingFromCatalog(@Param("accountId") Long accountId);
}
