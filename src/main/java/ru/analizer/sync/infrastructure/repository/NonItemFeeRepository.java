package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.sync.infrastructure.entity.NonItemFee;

public interface NonItemFeeRepository extends JpaRepository<NonItemFee, Long> {

    @Modifying
    @Query("delete from NonItemFee f where f.financeAccrual.id = :accrualId")
    void deleteByFinanceAccrualId(@Param("accrualId") Long accrualId);
}
