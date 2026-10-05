package ru.analizer.marketplace.ozon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ru.analizer.marketplace.AccrualDto;
import ru.analizer.marketplace.AccrualPage;
import ru.analizer.marketplace.AccrualTypeInfo;
import ru.analizer.marketplace.MarketplaceAdapter;
import ru.analizer.marketplace.MarketplaceCredentials;
import ru.analizer.marketplace.ozon.dto.AccrualType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Адаптер OZON. Отвечает только за транспорт и пагинацию, ничего не зная о базе данных.
 */
@Component
public class OzonAdapter implements MarketplaceAdapter {

    public static final String MARKETPLACE_CODE = "OZON";

    private static final Logger log = LoggerFactory.getLogger(OzonAdapter.class);

    private final OzonClient client;

    public OzonAdapter(OzonClient client) {
        this.client = client;
    }

    @Override
    public String marketplaceCode() {
        return MARKETPLACE_CODE;
    }

    @Override
    public void verifyCredentials(MarketplaceCredentials credentials) {
        // Один запрос справочника типов: ответ небольшой и доказывает, что ключи рабочие.
        client.getAccrualTypes(credentials);
    }

    @Override
    public List<AccrualTypeInfo> fetchAccrualTypes(MarketplaceCredentials credentials) {
        List<AccrualTypeInfo> types = new ArrayList<>();
        for (AccrualType remote : client.getAccrualTypes(credentials).safeAccrualTypes()) {
            if (remote.id() == null) {
                continue;
            }
            types.add(new AccrualTypeInfo(remote.id(), remote.name(), remote.description()));
        }
        return types;
    }

    /**
     * Обходит все страницы дня. У OZON пагинация только курсорная: {@code last_id} в ответе
     * означает, что есть следующая страница, пустая строка — что день вычитан полностью.
     * Дата между страницами обязана оставаться прежней, иначе OZON отвечает 400.
     */
    @Override
    public List<AccrualDto> fetchAccrualsByDay(MarketplaceCredentials credentials, LocalDate date) {
        List<AccrualDto> result = new ArrayList<>();
        String lastId = null;
        int pages = 0;
        do {
            AccrualPage page = client.getAccrualsByDay(credentials, date, lastId);
            pages++;
            result.addAll(page.accruals());
            lastId = page.hasNextPage() ? page.lastId() : null;
            log.debug("OZON {} страница {}: начислений с {}, lastId={}", date, pages, page.accruals().size(), lastId);
        } while (lastId != null);
        log.info("OZON {}: получено {} начислений за {} страниц(у)", date, result.size(), pages);
        return result;
    }
}
