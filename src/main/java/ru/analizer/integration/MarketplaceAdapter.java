package ru.analizer.integration;

import java.time.LocalDate;
import java.util.List;
import ru.analizer.account.domain.MarketplaceCredentials;
import ru.analizer.integration.model.AccrualDto;
import ru.analizer.integration.model.AccrualTypeInfo;

/**
 * Контракт интеграции с маркетплейсом. Аналитика и синхронизация зависят только от него,
 * поэтому добавление Wildberries или Яндекс Маркета не затрагивает основную логику.
 *
 * <p>Реквизиты передаются в каждый метод: у каждого пользователя свои ключи, поэтому
 * общий бин адаптера не может их содержать.
 */
public interface MarketplaceAdapter {

    /**
     * Код маркетплейса, как он хранится в таблице {@code marketplace}.
     */
    String marketplaceCode();

    /**
     * Проверяет реквизиты настоящим запросом.
     *
     * <p>Вызывается при подключении маркетплейса, чтобы неверный ключ обнаруживался
     * сразу, а не через неудачные импорты.
     *
     * @throws ru.analizer.integration.CredentialsRejectedException реквизиты не подошли
     */
    void verifyCredentials(MarketplaceCredentials credentials);

    /**
     * Справочник типов начислений. Список открыт и может пополняться, поэтому
     * бизнес-логика не должна опираться на захардкоженные значения.
     */
    List<AccrualTypeInfo> fetchAccrualTypes(MarketplaceCredentials credentials);

    /**
     * Все начисления за один день, с полной пагинацией.
     */
    List<AccrualDto> fetchAccrualsByDay(MarketplaceCredentials credentials, LocalDate date);
}