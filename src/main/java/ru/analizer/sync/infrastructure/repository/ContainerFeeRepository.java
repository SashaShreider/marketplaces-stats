package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.sync.infrastructure.entity.ContainerFee;

import java.util.List;

public interface ContainerFeeRepository extends JpaRepository<ContainerFee, Long> {

    /**
     * Удаляет сборы по начислениям.
     *
     * <p>Один запрос на весь день, а не по одному на начисление.
     */
    @Modifying
    @Query("delete from ContainerFee f where f.financeAccrual.id in :accrualIds")
    void deleteByFinanceAccrualIds(@Param("accrualIds") List<Long> accrualIds);
}
