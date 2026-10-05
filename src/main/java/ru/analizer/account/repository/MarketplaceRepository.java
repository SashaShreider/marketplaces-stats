package ru.analizer.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.account.domain.Marketplace;

import java.util.Optional;

public interface MarketplaceRepository extends JpaRepository<Marketplace, Long> {

    Optional<Marketplace> findByCode(String code);
}
