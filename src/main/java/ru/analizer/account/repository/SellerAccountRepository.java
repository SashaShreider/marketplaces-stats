package ru.analizer.account.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.analizer.account.domain.SellerAccount;

import java.util.List;
import java.util.Optional;

/**
 * Магазины пользователя.
 *
 * <p>Ключ аккаунта — пара «пользователь + маркетплейс + clientId»
 * ({@code uq_seller_account_user_marketplace_client}): несколько кабинетов на одной
 * платформе разрешены, один и тот же кабинет дважды — нет.
 *
 * <p>Выборки магазина подтягивают платформу: её код и название нужны в ответе, а
 * читать их с ленивого прокси вне транзакции нельзя — запрос упал бы с 500.
 */
public interface SellerAccountRepository extends JpaRepository<SellerAccount, Long> {

    /**
     * Магазин пользователя по идентификатору с проверкой владельца.
     *
     * <p>Владелец проверяется в самом запросе, а не сравнением потом. Идентификатор
     * приходит из пути запроса, поэтому условие {@code userId} — это и есть граница
     * доступа: запрос по чужому идентификатору и запрос по несуществующему дают
     * одинаковый пустой результат.
     */
    @Query("select a from SellerAccount a join fetch a.marketplace where a.id = :id and a.user.id = :userId")
    Optional<SellerAccount> findOwnedWithMarketplace(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Аккаунт пользователя на маркетплейсе с заданным идентификатором клиента.
     *
     * <p>Нужен для защиты от повторного подключения одного кабинета: уникальное
     * ограничение в базе не даст создать дубль, но заранее найденный аккаунт
     * позволяет ответить внятной ошибкой, а не положиться на нарушение ограничения.
     *
     * <p>Платформа подтягивается вместе: её код попадает в строку прогона импорта, и
     * читать его с ленивого прокси вне сессии нельзя.
     */
    @Query("select a from SellerAccount a join fetch a.marketplace "
            + "where a.user.id = :userId and a.marketplace.id = :marketplaceId and a.clientId = :clientId")
    Optional<SellerAccount> findOwnedWithMarketplace(
            @Param("userId") Long userId,
            @Param("marketplaceId") Long marketplaceId,
            @Param("clientId") String clientId);

    /**
     * Магазины пользователя — чтобы собрать его список.
     *
     * <p>Сортировка по платформе и затем по идентификатору: так магазины одной
     * площадки стоят рядом и в том же порядке, в каком были подключены. Порядок из
     * базы не гарантирован, а интерфейс обязан показывать список устойчиво — иначе
     * магазины прыгали бы местами при каждой перезагрузке страницы.
     */
    @Query("select a from SellerAccount a join fetch a.marketplace "
            + "where a.user.id = :userId order by a.marketplace.id, a.id")
    List<SellerAccount> findOwnedWithMarketplace(@Param("userId") Long userId);
}