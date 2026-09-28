package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.AccrualType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccrualTypeRepository extends JpaRepository<AccrualType, Long> {

    Optional<AccrualType> findByMarketplaceIdAndExternalTypeId(Long marketplaceId, Integer externalTypeId);

    List<AccrualType> findByMarketplaceId(Long marketplaceId);

    List<AccrualType> findByMarketplaceIdAndExternalTypeIdIn(Long marketplaceId, Collection<Integer> externalTypeIds);
}
