package ru.analizer.marketplace;

import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;

/**
 * Создание аккаунта продавца для маркетплейса, у которого ещё нет ни одного.
 *
 * <p>Отдельный компонент на каждый маркетплейс, потому что только он знает, где взять
 * реквизиты: у OZON это {@code Client-Id} из конфигурации, у другого маркетплейса
 * будет свой способ. Общему коду об этом знать незачем.
 */
public interface MarketplaceProvisioner {

    /** Обслуживает ли этот провайдер указанный маркетплейс. */
    boolean supports(String marketplaceCode);

    /**
     * Создаёт аккаунт продавца для маркетплейса.
     *
     * <p>Вызывается только когда аккаунтов ещё нет. Если реквизиты не заданы, реализация
     * обязана сообщить об этом понятной ошибкой, а не создать запись с пустым
     * идентификатором.
     */
    SellerAccount provision(Marketplace marketplace);
}