package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.ImportedDay;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ImportedDayRepository extends JpaRepository<ImportedDay, Long> {

    Optional<ImportedDay> findBySellerAccountIdAndDay(Long sellerAccountId, LocalDate day);

    List<ImportedDay> findBySellerAccountIdAndDayBetween(Long sellerAccountId, LocalDate from, LocalDate to);
}