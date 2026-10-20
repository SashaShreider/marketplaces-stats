package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.sync.infrastructure.entity.PostingProduct;

public interface PostingProductRepository extends JpaRepository<PostingProduct, Long> {
}
