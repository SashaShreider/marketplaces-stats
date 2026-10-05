package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.persistence.entity.OzonProductAttribute;

import java.util.List;

public interface OzonProductAttributeRepository extends JpaRepository<OzonProductAttribute, Long> {

    List<OzonProductAttribute> findBySellerAccountIdAndSku(Long sellerAccountId, long sku);

    List<OzonProductAttribute> findBySellerAccountIdAndAttributeId(Long sellerAccountId, long attributeId);

    /**
     * Перезаписывает характеристики товара целиком.
     *
     * <p>Обновляем, а не дополняем: состав атрибутов меняется при смене категории товара,
     * и оставшиеся старые строки исказили бы карточку. Порядок важен — после удалений
     * нужен flush, иначе новая строка вставится раньше, чем удалится прежняя, и упрётся
     * в уникальный индекс.
     */
    @Modifying
    @Query("""
            delete from OzonProductAttribute a
            where a.sellerAccountId = :accountId and a.sku = :sku
            """)
    int deleteForProduct(@Param("accountId") Long accountId, @Param("sku") long sku);
}