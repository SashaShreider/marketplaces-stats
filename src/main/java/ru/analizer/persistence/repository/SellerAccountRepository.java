package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.SellerAccount;

import java.util.Optional;

public interface SellerAccountRepository extends JpaRepository<SellerAccount, Long> {

    Optional<SellerAccount> findByMarketplaceIdAndClientId(Long marketplaceId, String clientId);
}
