package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.sync.infrastructure.entity.FinanceAccrual;

import java.util.Optional;

public interface FinanceAccrualRepository extends JpaRepository<FinanceAccrual, Long> {

    Optional<FinanceAccrual> findBySellerAccountIdAndExternalId(Long sellerAccountId, Long externalId);
}
