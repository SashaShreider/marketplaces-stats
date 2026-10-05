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
 * <p>TODO(#single-env-fallback): пока поддерживается только один аккаунт на
 * развёртывание. Если понадобится режим «один продавец, реквизиты в .env», сюда
 * добавляется чтение из конфигурации как запасной источник.
 */
@Component
public class OzonAccountProvisioner implements MarketplaceProvisioner {

    private final SellerAccountRepository sellerAccountRepository;

    public OzonAccountProvisioner(SellerAccountRepository sellerAccountRepository) {
        this.sellerAccountRepository = sellerAccountRepository;
    }

    @Override
    public boolean supports(String marketplaceCode) {
        return OzonAdapter.MARKETPLACE_CODE.equalsIgnoreCase(marketplaceCode);
    }

    @Override
    public SellerAccount provision(Marketplace marketplace, AppUser user,
                                   ru.analizer.account.domain.MarketplaceCredentials credentials) {
        return sellerAccountRepository.save(new SellerAccount(
                user, marketplace, "OZON " + credentials.clientId(),
                credentials.clientId(), credentials.apiKey()));
    }
}