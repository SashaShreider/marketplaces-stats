package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.sync.infrastructure.entity.FinanceAccrual;

import java.util.Collection;
import java.util.List;

public interface FinanceAccrualRepository extends JpaRepository<FinanceAccrual, Long> {

    /** Начисления дня по внешним идентификаторам — одним запросом, а не по одному. */
    @Query("""
            select a from FinanceAccrual a
            where a.sellerAccount.id = :accountId and a.externalId in :externalIds
            """)
    List<FinanceAccrual> findByExternalIds(@Param("accountId") Long accountId,
                                           @Param("externalIds") Collection<Long> externalIds);
}
