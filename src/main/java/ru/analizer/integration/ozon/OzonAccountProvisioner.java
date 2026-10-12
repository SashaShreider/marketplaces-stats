package ru.analizer.integration.ozon;

import org.springframework.stereotype.Component;
import ru.analizer.account.domain.Marketplace;
import ru.analizer.account.domain.MarketplaceProvisioner;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.account.repository.SellerAccountRepository;
import ru.analizer.auth.domain.AppUser;

/**
 * Создание аккаунта продавца OZON.
 *
 * <p>Реквизиты приходят из запроса пользователя, а не из конфигурации: их вводит сам
 * пользователь, и он же может их заменить — ключи у OZON истекают.
 *
 * <p>Название магазина берётся у OZON при подключении. Раньше оно собиралось из кода
 * маркетплейса и Client-Id («OZON 1154»), и это было не название магазина, а
 * технический идентификатор: в интерфейсе он стоял рядом с настоящим наименованием
 * продавца и вводил в заблуждение. Спрашивать дороже одного запроса, зато магазин
 * сразу узнаёт себя в интерфейсе по тому имени, которым называет себя сам.
 *
 * <p>TODO(#single-env-fallback): пока поддерживается только один аккаунт на
 * развёртывание. Если понадобится режим «один продавец, реквизиты в .env», сюда
 * добавляется чтение из конфигурации как запасной источник.
 */
@Component
public class OzonAccountProvisioner implements MarketplaceProvisioner {

    private final SellerAccountRepository sellerAccountRepository;
    private final OzonClient client;

    public OzonAccountProvisioner(SellerAccountRepository sellerAccountRepository, OzonClient client) {
        this.sellerAccountRepository = sellerAccountRepository;
        this.client = client;
    }

    @Override
    public boolean supports(String marketplaceCode) {
        return OzonAdapter.MARKETPLACE_CODE.equalsIgnoreCase(marketplaceCode);
    }

    @Override
    public SellerAccount provision(Marketplace marketplace, AppUser user,
                                   ru.analizer.account.domain.MarketplaceCredentials credentials) {
        return sellerAccountRepository.save(new SellerAccount(
                user, marketplace, resolveShopName(credentials),
                credentials.clientId(), credentials.apiKey()));
    }

    @Override
    public String resolveShopName(Marketplace marketplace,
                                  ru.analizer.account.domain.MarketplaceCredentials credentials) {
        return resolveShopName(credentials);
    }

    /**
     * Название магазина от OZON, с запасным вариантом.
     *
     * <p>Если OZON не вернул название — у продавца-ИП, например, — аккаунт всё равно
     * заводится: отказ из-за отсутствия необязательной строки означал бы, что
     * подключиться нельзя вовсе. Тогда остаётся прежнее «OZON + Client-Id»: пустое имя
     * хуже, потому что в интерфейсе магазин был бы безымянным, и непонятно, один это
     * аккаунт или несколько.
     *
     * <p>Ошибку самого запроса глушить нельзя: её главный смысл в том, чтобы
     * неверные ключи обнаружились здесь, при подключении, а не на первом импорте.
     */
    private String resolveShopName(ru.analizer.account.domain.MarketplaceCredentials credentials) {
        String fromOzon = client.getShopName(credentials);
        return fromOzon != null ? fromOzon : "OZON " + credentials.clientId();
    }
}