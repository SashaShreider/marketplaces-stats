package ru.analizer.sync.infrastructure.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.sync.infrastructure.entity.PostingProduct;

public interface PostingProductRepository extends JpaRepository<PostingProduct, Long> {

    @Modifying
    @Query("delete from PostingProduct p where p.posting.id = :postingId")
    void deleteByPostingId(@Param("postingId") Long postingId);
}
