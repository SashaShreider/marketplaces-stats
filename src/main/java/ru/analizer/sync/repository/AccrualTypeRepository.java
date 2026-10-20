package ru.analizer.sync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.sync.domain.AccrualType;

import java.util.List;

public interface AccrualTypeRepository extends JpaRepository<AccrualType, Long> {

    List<AccrualType> findByMarketplaceId(Long marketplaceId);
}
