package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.Marketplace;

import java.util.Optional;

public interface MarketplaceRepository extends JpaRepository<Marketplace, Long> {

    Optional<Marketplace> findByCode(String code);
}
