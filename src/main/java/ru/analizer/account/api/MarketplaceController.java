package ru.analizer.account.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.account.domain.AccountLookup;
import ru.analizer.account.domain.Marketplace;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.account.repository.MarketplaceRepository;

import java.util.List;
import java.util.Set;

/**
 * Какие маркетплейсы поддерживаются системой.
 *
 * <p>Справочник платформ, а не список магазинов: список магазинов пользователя
 * возвращает {@link AccountController}. Разделено потому, что справочник отвечает на
 * вопрос «куда вообще можно подключиться», а список магазинов — «с каким из них я
 * работаю». Одним ответом это плохо выражалось: у одного маркетплейса теперь может
 * быть несколько магазинов, и в одном списке они смешались бы с платформами, к
 * которым ещё даже не подключались.
 *
 * <p>Список показывает все платформы системы, а не только те, что используются:
 * иначе пользователь не увидит, что Wildberries поддерживается, и не поймёт, куда идти
 * подключаться. Отличается поле {@code connected}.
 *
 * <p>Собственных запросов к маркетплейсам не выполняет: только состояние нашей базы.
 */
@RestController
@RequestMapping("/api/marketplaces")
public class MarketplaceController {

    private final MarketplaceRepository marketplaceRepository;
    private final AccountLookup accountLookup;

    public MarketplaceController(MarketplaceRepository marketplaceRepository,
                                 AccountLookup accountLookup) {
        this.marketplaceRepository = marketplaceRepository;
        this.accountLookup = accountLookup;
    }

    /**
     * GET /api/marketplaces
     *
     * <p>Позволяет клиенту построить окно подключения, не зная заранее кодов.
     */
    @GetMapping
    public List<MarketplaceInfo> list() {
        // Один выбор магазинов пользователя на весь ответ: иначе на каждую платформу
        // делался бы свой запрос, хотя аккаунты нужны только чтобы отличить «есть
        // магазин» от «нет ни одного».
        Set<Long> withShops = accountLookup.findAccounts().stream()
                .map(a -> a.getMarketplace().getId())
                .collect(java.util.stream.Collectors.toSet());

        return marketplaceRepository.findAll().stream()
                .map(m -> toInfo(m, withShops.contains(m.getId())))
                .toList();
    }

    private MarketplaceInfo toInfo(Marketplace marketplace, boolean connected) {
        return new MarketplaceInfo(marketplace.getCode(), marketplace.getName(), connected);
    }

    /**
     * @param code      код маркетплейса; подставляется в тело запроса при подключении
     * @param name      название для интерфейса
     * @param connected есть ли у пользователя хотя бы один магазин на этой платформе
     */
    public record MarketplaceInfo(
            String code,
            String name,
            boolean connected
    ) {
    }
}