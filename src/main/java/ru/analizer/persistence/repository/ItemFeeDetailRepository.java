package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.ItemFeeDetail;

public interface ItemFeeDetailRepository extends JpaRepository<ItemFeeDetail, Long> {
}
