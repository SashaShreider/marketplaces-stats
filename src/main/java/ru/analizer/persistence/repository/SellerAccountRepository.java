package ru.analizer.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.analizer.persistence.entity.SellerAccount;

import java.util.List;
import java.util.Optional;

public interface SellerAccountRepository extends JpaRepository<SellerAccount, Long> {

    /**
     * Аккаунты маркетплейса.
     *
     * <p>Метод возвращает список, а не один аккаунт, хотя схема предполагает единственный:
     * если аккаунтов окажется больше, вызывающий обязан это заметить и сообщить, а не
     * молча взять первый.
     */
    List<SellerAccount> findByMarketplaceId(Long marketplaceId);

    Optional<SellerAccount> findByMarketplaceIdAndClientId(Long marketplaceId, String clientId);
}