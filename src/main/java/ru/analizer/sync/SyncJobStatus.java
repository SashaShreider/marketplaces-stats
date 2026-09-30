package ru.analizer.sync;

import ru.analizer.persistence.entity.JobStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Состояние фоновой задачи — то, что видит пользователь во время загрузки.
 *
 * @param inProgress признак «идёт загрузка»: клиент по нему показывает уведомление
 */
public record SyncJobStatus(
        Long id,
        String marketplace,
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