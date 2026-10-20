package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.sync.infrastructure.entity.ItemFee;

import java.util.List;

public interface ItemFeeRepository extends JpaRepository<ItemFee, Long> {

    /**
     * Удаляет списания по начислениям.
     *
     * <p>Один запрос на весь день, а не по одному на начисление. Детализация списания
     * уходит каскадом вместе с ним.
     */
    @Modifying
    @Query("delete from ItemFee f where f.financeAccrual.id in :accrualIds")
    void deleteByFinanceAccrualIds(@Param("accrualIds") List<Long> accrualIds);
}
