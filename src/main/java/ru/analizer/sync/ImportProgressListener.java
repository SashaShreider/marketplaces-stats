package ru.analizer.sync;

import java.time.LocalDate;

/**
 * Получатель прогресса загрузки периода.
 *
 * <p>Фоновая задача реализует его, чтобы отчёт мог показывать «12 из 30 дней»,
 * пока HTTP-запрос пользователя давно завершился.
 */
public interface ImportProgressListener {

    /** Начало загрузки периода. */
    void onStart(int totalDays, int daysToFetch);

    /** Взялись за день: он сейчас обрабатывается. */
    void onDayStart(LocalDate day, int processedDays, int daysToFetch);

    /** День успешно загружен. */
    void onDayDone(LocalDate day, int processedDays, int daysToFetch);

    /** День не удалось загрузить: он числится пропущенным, а не пустым. */
    void onDayFailed(LocalDate day, RuntimeException error);
}