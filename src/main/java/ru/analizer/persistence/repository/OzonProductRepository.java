package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.OzonProduct;

import java.util.Optional;

public interface OzonProductRepository extends JpaRepository<OzonProduct, Long> {

    Optional<OzonProduct> findBySellerAccountIdAndSku(Long sellerAccountId, long sku);

    long countBySellerAccountId(Long sellerAccountId);

    Optional<OzonProduct> findFirstBySellerAccountIdAndOfferId(Long sellerAccountId, String offerId);
}