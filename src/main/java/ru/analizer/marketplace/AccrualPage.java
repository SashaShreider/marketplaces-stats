package ru.analizer.marketplace;

import java.util.List;

/**
 * Страница начислений, полученная одним ответом API. Нужна для диагностики пагинации:
 * по ней видно, сколько страниц пройдено и сколько операций пришло.
 */
public record AccrualPage(List<AccrualDto> accruals, String lastId) {

    public boolean hasNextPage() {
        return lastId != null && !lastId.isBlank();
    }
}
