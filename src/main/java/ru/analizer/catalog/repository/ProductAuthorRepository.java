package ru.analizer.catalog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.catalog.domain.ProductAuthor;

import java.util.List;

public interface ProductAuthorRepository extends JpaRepository<ProductAuthor, Long> {

    List<ProductAuthor> findBySellerAccountIdAndSkuOrderByPositionAsc(Long sellerAccountId, long sku);

    /**
     * Перезаписывает авторов товара целиком.
     *
     * <p>Аналогично характеристикам: продавец мог исправить карточку, и прежние строки
     * об авторах тогда остались бы в отчёте навсегда.
     */
    @Modifying
    @Query("""
            delete from ProductAuthor a
            where a.sellerAccountId = :accountId and a.sku = :sku
            """)
    int deleteForProduct(@Param("accountId") Long accountId, @Param("sku") long sku);
}