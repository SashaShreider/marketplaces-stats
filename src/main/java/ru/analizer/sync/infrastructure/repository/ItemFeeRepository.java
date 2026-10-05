package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.sync.infrastructure.entity.ItemFee;

public interface ItemFeeRepository extends JpaRepository<ItemFee, Long> {

    @Modifying
    @Query("delete from ItemFee f where f.financeAccrual.id = :accrualId")
    void deleteByFinanceAccrualId(@Param("accrualId") Long accrualId);
}
