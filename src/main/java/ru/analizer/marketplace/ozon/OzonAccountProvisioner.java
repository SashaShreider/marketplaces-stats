package ru.analizer.marketplace.ozon;

import org.springframework.stereotype.Component;
import ru.analizer.marketplace.MarketplaceProvisioner;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.SellerAccountRepository;

/**
 * Создание аккаунта продавца OZON.
 *
 * <p>Идентификатор берётся из конфигурации: у каждого развёртывания он свой, и держать
 * его в базе или в коде одинаково неправильно. Пока это единственный аккаунт на
 * развёртывание — схема рассчитана на один аккаунт на маркетплейс.
 */
@Component
public class OzonAccountProvisioner implements MarketplaceProvisioner {

    private final OzonProperties properties;
    private final SellerAccountRepository sellerAccountRepository;

    public OzonAccountProvisioner(OzonProperties properties,
                                  SellerAccountRepository sellerAccountRepository) {
        this.properties = properties;
        this.sellerAccountRepository = sellerAccountRepository;
    }

    @Override
    public boolean supports(String marketplaceCode) {
        return OzonAdapter.MARKETPLACE_CODE.equalsIgnoreCase(marketplaceCode);
    }

    @Override
    public SellerAccount provision(Marketplace marketplace) {
        if (!properties.isConfigured()) {
            // Без Client-Id и Api-Key импорт всё равно не сработал бы, поэтому
            // сообщаем об этом сразу и понятным текстом.
            throw new OzonNotConfiguredException();
        }
        String clientId = properties.clientId();
        return sellerAccountRepository.save(
                new SellerAccount(marketplace, "OZON " + clientId, clientId));
    }
}