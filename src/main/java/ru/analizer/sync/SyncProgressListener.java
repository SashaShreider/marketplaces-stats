package ru.analizer.sync;

import java.time.LocalDate;

/**
 * Получатель прогресса загрузки.
 *
 * <p>Фоновая задача реализует его, чтобы отчёт мог показывать «12 из 30 дней»,
 * пока HTTP-запрос пользователя давно завершился.
 */
public interface SyncProgressListener {

    void onStart(int totalDays, int daysToFetch);

    void onDayStart(LocalDate day, int processedDays, int daysToFetch);

    void onDayFailed(LocalDate day, RuntimeException error);
}