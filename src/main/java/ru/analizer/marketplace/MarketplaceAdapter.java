package ru.analizer.marketplace;

import java.time.LocalDate;
import java.util.List;

/**
 * Контракт интеграции с маркетплейсом. Аналитика и синхронизация зависят только от него,
 * поэтому добавление Wildberries или Яндекс Маркета не затрагивает основную логику.
 */
public interface MarketplaceAdapter {

    /**
     * Код маркетплейса, как он хранится в таблице {@code marketplace}.
     */
    String marketplaceCode();

    /**
     * Справочник типов начислений. Список открыт и может пополняться, поэтому
     * бизнес-логика не должна опираться на захардкоженные значения.
     */
    List<AccrualTypeInfo> fetchAccrualTypes();

    /**
     * Все начисления за один день, с полной пагинацией.
     */
    List<AccrualDto> fetchAccrualsByDay(LocalDate date);
}
