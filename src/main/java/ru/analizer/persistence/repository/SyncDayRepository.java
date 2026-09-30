package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.SyncDay;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SyncDayRepository extends JpaRepository<SyncDay, Long> {

    Optional<SyncDay> findBySellerAccountIdAndDay(Long sellerAccountId, LocalDate day);

    List<SyncDay> findBySellerAccountIdAndDayBetween(Long sellerAccountId, LocalDate from, LocalDate to);
}