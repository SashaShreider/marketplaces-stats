package ru.analizer.account.domain;

import ru.analizer.account.domain.Marketplace;
import ru.analizer.auth.domain.AppUser;
import ru.analizer.account.domain.SellerAccount;

/**
 * Создание аккаунта продавца для маркетплейса, у которого ещё нет ни одного.
 *
 * <p>Отдельный компонент на каждый маркетплейс, потому что только он знает, как
 * назвать аккаунт и что требуется для подключения. Общему коду об этом знать незачем.
 */
public interface MarketplaceProvisioner {

    /** Обслуживает ли этот провайдер указанный маркетплейс. */
    boolean supports(String marketplaceCode);

    /**
     * Создаёт аккаунт продавца.
     *
     * @param credentials реквизиты, введённые пользователем
     */
    SellerAccount provision(Marketplace marketplace, AppUser user, MarketplaceCredentials credentials);
}