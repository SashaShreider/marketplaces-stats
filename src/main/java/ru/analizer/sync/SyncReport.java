package ru.analizer.sync;

import java.time.LocalDate;

/**
 * Итог одной синхронизации. Нужен, чтобы отличить «пришло 0 начислений» от
 * «пришло 0, потому что запрос упал» — технический пробел не должен выглядеть
 * как отсутствие начислений.
 */
public record SyncReport(
        String marketplace,
        LocalDate dateFrom,
        LocalDate dateTo,
        int days,
        int accrualsReceived,
        int accrualsInserted,
        int accrualsUpdated,
        int accrualsSkipped,
        int pages
) {

    public boolean isEmpty() {
        return accrualsReceived == 0;
    }
}
