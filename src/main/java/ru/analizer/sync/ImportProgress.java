package ru.analizer.sync;

import ru.analizer.persistence.entity.ImportType;
import ru.analizer.persistence.entity.RunState;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Прогресс фонового импорта — то, что видит клиент во время загрузки.
 *
 * @param totalUnits   сколько единиц работы всего: дней у финансового импорта,
 *                     товаров у импорта каталога. Что именно — показывает {@code importType}
 * @param doneUnits    успешно обработано
 * @param skippedUnits пропущено как уже загруженное
 * @param failedUnits  не удалось обработать; такие единицы числятся неудавшимися, а не пустыми
 * @param currentUnit  дата в работе у финансового импорта, SKU — у каталога
 * @param inProgress   признак «идёт импорт»: клиент по нему показывает прогресс
 */
public record ImportProgress(
        Long id,
        String marketplace,
        ImportType importType,
        LocalDate dateFrom,
        LocalDate dateTo,
        RunState status,
        int totalUnits,
        int doneUnits,
        int skippedUnits,
        int failedUnits,
        String currentUnit,
        String error,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        boolean inProgress
) {

    /** Готово ли это состояние для показа: импорт ещё не начался или уже закончился. */
    public boolean finished() {
        return !inProgress;
    }

    /** Доля готовности в процентах, 0–100. Удобно для progressbar без вычислений на клиенте. */
    @com.fasterxml.jackson.annotation.JsonProperty
    public int percent() {
        if (totalUnits <= 0) {
            return 0;
        }
        int processed = doneUnits + skippedUnits + failedUnits;
        return Math.min(100, (int) Math.round(processed * 100.0 / totalUnits));
    }
}