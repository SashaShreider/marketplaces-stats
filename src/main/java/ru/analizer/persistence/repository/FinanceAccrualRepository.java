package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.persistence.entity.FinanceAccrual;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FinanceAccrualRepository extends JpaRepository<FinanceAccrual, Long> {

    Optional<FinanceAccrual> findBySellerAccountIdAndExternalId(Long sellerAccountId, Long externalId);

    List<FinanceAccrual> findBySellerAccountIdAndAccrualDateBetween(
            Long sellerAccountId, LocalDate from, LocalDate to);

    boolean existsBySellerAccountIdAndAccrualDate(Long sellerAccountId, LocalDate date);

    @Query("""
            select a.accruedCategory, count(a)
            from FinanceAccrual a
            where a.sellerAccount.id = :accountId and a.accrualDate between :from and :to
            group by a.accruedCategory
            """)
    List<Object[]> countByCategory(@Param("accountId") Long accountId,
                                   @Param("from") LocalDate from,
                                   @Param("to") LocalDate to);

    /**
     * Начисления за день без вложенных сущностей — для сверки количества операций.
     */
    @Query("""
            select count(a) from FinanceAccrual a
            where a.sellerAccount.id = :accountId and a.accrualDate = :date
            """)
    long countByDate(@Param("accountId") Long accountId, @Param("date") LocalDate date);

    @Query("""
            select a from FinanceAccrual a
            where a.sellerAccount.id = :accountId and a.externalId in :externalIds
            """)
    List<FinanceAccrual> findByExternalIds(@Param("accountId") Long accountId,
                                           @Param("externalIds") Collection<Long> externalIds);
}
