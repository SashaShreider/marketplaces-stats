package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.SyncJob;

import java.util.List;

public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {

    List<SyncJob> findTop20ByOrderByCreatedAtDesc();

    java.util.Optional<SyncJob> findTopByOrderByCreatedAtDesc();
}