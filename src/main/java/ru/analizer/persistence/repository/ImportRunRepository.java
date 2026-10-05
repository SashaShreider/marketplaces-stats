package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.persistence.entity.ImportRun;
import ru.analizer.persistence.entity.ImportType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ImportRunRepository extends JpaRepository<ImportRun, Long> {

    /** Последние прогоны аккаунта, новые сверху. */
    List<ImportRun> findTop20BySellerAccountIdOrderByCreatedAtDesc(Long sellerAccountId);

    /** Сколько прогонов запускалось у аккаунта за всё время. */
    long countBySellerAccountId(Long sellerAccountId);

    /**
     * Прогон конкретного аккаунта.
     *
     * <p>Проверка принадлежности обязательна: по одному номеру прогона нельзя читать
     * прогресс чужого маркетплейса.
     */
    Optional<ImportRun> findByIdAndSellerAccountId(Long id, Long sellerAccountId);

    /**
     * Незавершённый финансовый импорт того же аккаунта, период которого пересекается
     * с запрошенным.
     *
     * <p>Ищем именно пересечение, а не совпадение: прогоны за сентябрь и за конец августа
     * конфликтуют по общим дням ровно так же, как два прогона за один и тот же период.
     *
     * <p>Импорты каталога исключены явно: у них дат нет, и условие пересечения дат для
     * них бессмысленно — они не должны блокировать финансовый импорт.
     */
    @Query("""
            select r from ImportRun r
            where r.sellerAccount.id = :accountId
              and r.importType = ru.analizer.persistence.entity.ImportType.FINANCE
              and r.status in (ru.analizer.persistence.entity.RunState.PENDING,
                               ru.analizer.persistence.entity.RunState.RUNNING)
              and r.dateFrom <= :to
              and r.dateTo >= :from
            order by r.createdAt desc
            """)
    Optional<ImportRun> findActiveOverlapping(@Param("accountId") Long accountId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * Незавершённый импорт данного вида у аккаунта.
     *
     * <p>Даты не сравниваются: у каталога их нет, поэтому любая активная задача каталога
     * конфликтует с новой.
     */
    @Query("""
            select r from ImportRun r
            where r.sellerAccount.id = :accountId
              and r.importType = :importType
              and r.status in (ru.analizer.persistence.entity.RunState.PENDING,
                               ru.analizer.persistence.entity.RunState.RUNNING)
            order by r.createdAt desc
            """)
    Optional<ImportRun> findFirstActiveOfType(@Param("accountId") Long accountId,
                                              @Param("importType") ImportType importType);
}