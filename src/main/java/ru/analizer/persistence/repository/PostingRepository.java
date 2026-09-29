package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.Posting;

import java.util.Optional;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    Optional<Posting> findByFinanceAccrualId(Long financeAccrualId);
}
