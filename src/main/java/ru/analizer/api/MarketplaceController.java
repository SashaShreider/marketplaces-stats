package ru.analizer.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.persistence.AccountLookup;
import ru.analizer.persistence.entity.Marketplace;
import ru.analizer.persistence.entity.SellerAccount;
import ru.analizer.persistence.repository.ImportRunRepository;
import ru.analizer.persistence.repository.MarketplaceRepository;
import ru.analizer.persistence.repository.OzonProductRepository;

import java.util.List;
import java.util.Optional;

/**
 * Что доступно текущему пользователю.
 *
 * <p>Единственный метод без сегмента маркетплейса: он отвечает на вопрос «что у меня уже
 * подключено», поэтому вызывается раньше, чем известен конкретный маркетплейс.
 *
 * <p>Список показывает все маркетплейсы системы, а не только подключённые: иначе
 * пользователь не увидит, что Wildberries вообще поддерживается, и не поймёт, куда
 * идти подключаться. Отличается поле {@code connected}.
 *
 * <p>Собственных запросов к маркетплейсам не выполняет: только состояние нашей базы.
 */
@RestController
@RequestMapping("/api/marketplaces")
public class MarketplaceController {

    private final MarketplaceRepository marketplaceRepository;
    private final AccountLookup accountLookup;
    private final ImportRunRepository importRunRepository;
    private final OzonProductRepository ozonProductRepository;

    public MarketplaceController(MarketplaceRepository marketplaceRepository,
                                 AccountLookup accountLookup,
                                 ImportRunRepository importRunRepository,
                                 OzonProductRepository ozonProductRepository) {
        this.marketplaceRepository = marketplaceRepository;
        this.accountLookup = accountLookup;
        this.importRunRepository = importRunRepository;
        this.ozonProductRepository = ozonProductRepository;
    }

    /**
     * GET /api/marketplaces
     *
     * <p>Позволяет клиенту построить выбор маркетплейса, не зная заранее кодов.
     * Поле {@code connected} показывает, можно ли запускать импорт.
     */
    @GetMapping
    public List<MarketplaceInfo> list() {
        return marketplaceRepository.findAll().stream()
                .map(this::toInfo)
                .toList();
    }

    private MarketplaceInfo toInfo(Marketplace marketplace) {
        // Ищем аккаунт именно текущего пользователя: чужие подключения в ответе
        // показывать нельзя, это утечка самого факта работы магазина.
        Optional<SellerAccount> account = accountLookup.findAccount(marketplace.getCode());

        return new MarketplaceInfo(
                marketplace.getCode(),
                marketplace.getName(),
                account.isPresent(),
                account.map(SellerAccount::getName).orElse(null),
                account.map(SellerAccount::getClientId).orElse(null),
                account.map(a -> ozonProductRepository.countBySellerAccountId(a.getId())).orElse(0L),
                account.map(a -> importRunRepository.countBySellerAccountId(a.getId())).orElse(0L));
    }

    /**
     * @param code            код маркетплейса; подставляется в путь следующих запросов
     * @param name            название для интерфейса
     * @param connected       задан ли аккаунт; при {@code false} импорт вернёт 409
     *                       с просьбой подключить маркетплейс
     * @param accountName     имя подключённого аккаунта
     * @param clientId        идентификатор клиента; секретный ключ не возвращается
     * @param catalogProducts товаров в каталоге; 0 означает, что каталог ещё не импортирован
     * @param importRuns      сколько импортов запускалось за всё время
     */
    public record MarketplaceInfo(
            String code,
            String name,
            boolean connected,
            String accountName,
            String clientId,
            long catalogProducts,
            long importRuns
    ) {
    }
}