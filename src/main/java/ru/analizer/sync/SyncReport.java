package ru.analizer.sync;

import java.time.LocalDate;

/**
 * Итог одной синхронизации.
 *
 * <p>Нужен, чтобы отличить «пришло 0 начислений» от «пришло 0, потому что запрос упал»:
 * технический пробел не должен выглядеть как отсутствие начислений.
 *
 * @param requestedDays сколько дней в запрошенном периоде
 * @param syncedDays    сколько дней реально догружено этим запуском
 * @param complete      загружен ли весь период
 */
public record SyncReport(
        String marketplace,
        LocalDate dateFrom,
        LocalDate dateTo,
        int requestedDays,
        int accrualsReceived,
        int accrualsInserted,
        int accrualsUpdated,
        int accrualsSkipped,
        int pages,
        int syncedDays,
        boolean complete
) {
}