package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.persistence.entity.SyncJob;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    List<SyncJob> findTop20ByOrderByCreatedAtDesc();

    /**
     * Незавершённая задача того же аккаунта, период которой пересекается с запрошенным.
     *
     * <p>Ищем именно пересечение, а не совпадение: задачи за сентябрь и за конец августа
     * конфликтуют по общим дням ровно так же, как две задачи за один и тот же период.
     */
    @Query("""
            select j from SyncJob j
            where j.sellerAccount.id = :accountId
              and j.status in (ru.analizer.persistence.entity.JobStatus.PENDING,
                               ru.analizer.persistence.entity.JobStatus.RUNNING)
              and j.dateFrom <= :to
              and j.dateTo >= :from
            order by j.createdAt desc
            """)
    Optional<SyncJob> findActiveOverlapping(@Param("accountId") Long accountId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);
}