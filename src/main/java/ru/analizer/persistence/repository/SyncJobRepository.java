package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.persistence.entity.JobType;
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
     *
     * <p>Задачи каталога исключены явно: у них дат нет, и условие пересечения дат для
     * них бессмысленно — они не должны блокировать финансовую загрузку.
     */
    @Query("""
            select j from SyncJob j
            where j.sellerAccount.id = :accountId
              and j.jobType = ru.analizer.persistence.entity.JobType.FINANCE
              and j.status in (ru.analizer.persistence.entity.JobStatus.PENDING,
                               ru.analizer.persistence.entity.JobStatus.RUNNING)
              and j.dateFrom <= :to
              and j.dateTo >= :from
            order by j.createdAt desc
            """)
    Optional<SyncJob> findActiveOverlapping(@Param("accountId") Long accountId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    /**
     * Незавершённая загрузка каталога того же аккаунта.
     *
     * <p>Даты не сравниваются: каталог у аккаунта один, поэтому любая активная задача
     * каталога конфликтует с новой.
     */
    @Query("""
            select j from SyncJob j
            where j.sellerAccount.id = :accountId
              and j.jobType = :jobType
              and j.status in (ru.analizer.persistence.entity.JobStatus.PENDING,
                               ru.analizer.persistence.entity.JobStatus.RUNNING)
            order by j.createdAt desc
            """)
    Optional<SyncJob> findFirstActiveOfType(@Param("accountId") Long accountId,
                                            @Param("jobType") JobType jobType);
}