package ru.analizer.sync;

import ru.analizer.persistence.entity.JobStatus;
import ru.analizer.persistence.entity.JobType;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Состояние фоновой задачи — то, что видит пользователь во время загрузки.
 *
 * @param totalDays для финансовой задачи — дни, для задачи каталога — товары;
 *                  счётчик один, потому что показывать прогресс нужно в одном поле,
 *                  а вид задачи известен из {@code jobType}
 * @param inProgress признак «идёт загрузка»: клиент по нему показывает уведомление
 */
public record SyncJobStatus(
        Long id,
        String marketplace,
        JobType jobType,
        LocalDate dateFrom,
        LocalDate dateTo,
        JobStatus status,
        int totalDays,
        int doneDays,
        int skippedDays,
        int failedDays,
        LocalDate currentDay,
        String error,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        boolean inProgress
) {
    /** Готово ли это состояние для показа: задача ещё не началась или уже закончилась. */
    public boolean finished() {
        return !inProgress;
    }
}