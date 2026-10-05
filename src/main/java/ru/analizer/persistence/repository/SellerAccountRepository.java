package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.SellerAccount;

import java.util.List;
import java.util.Optional;

public interface SellerAccountRepository extends JpaRepository<SellerAccount, Long> {

    /**
     * Аккаунт пользователя на маркетплейсе.
     *
     * <p>Метод один на пользователя и маркетплейс — это гарантирует уникальное
     * ограничение в базе, иначе Spring Data не смогла бы выбрать единственный.
     */
    Optional<SellerAccount> findByUserIdAndMarketplaceId(Long userId, Long marketplaceId);

    /** Аккаунты пользователя — чтобы собрать его список маркетплейсов. */
    List<SellerAccount> findByUserId(Long userId);
}