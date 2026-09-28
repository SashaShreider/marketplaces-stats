package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.DeliveryService;

public interface DeliveryServiceRepository extends JpaRepository<DeliveryService, Long> {
}
