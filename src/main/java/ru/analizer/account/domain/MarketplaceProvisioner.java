package ru.analizer.account.domain;

import ru.analizer.auth.domain.AppUser;

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

    /**
     * Название магазина по его реквизитам.
     *
     * <p>Отдельный метод нужен уже подключённому аккаунту: его название тоже нужно
     * знать, а создавать второй раз нельзя. Без этого аккаунт, заведённый до
     * появления настоящих имён, остался бы с техническим идентификатором навсегда —
     * переименовать его можно было бы только удалением вместе с данными.
     *
     * <p>По умолчанию — то же, из чего имя складывалось раньше: маркетплейс и
     * Client-Id. Провайдер, умеющий спросить настоящее имя у маркетплейса,
     * переопределяет метод.
     */
    default String resolveShopName(Marketplace marketplace, MarketplaceCredentials credentials) {
        return marketplace.getName() + " " + credentials.clientId();
    }
}