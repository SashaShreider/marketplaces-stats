package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.sync.infrastructure.entity.Posting;

import java.util.Optional;

public interface PostingRepository extends JpaRepository<Posting, Long> {

    Optional<Posting> findByFinanceAccrualId(Long financeAccrualId);
}
