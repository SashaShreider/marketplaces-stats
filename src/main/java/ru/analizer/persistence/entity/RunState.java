package ru.analizer.persistence.entity;

/**
 * Состояние фоновой задачи загрузки.
 */
public enum RunState {
    /** Создана, ждёт запуска. */
    PENDING,
    /** Идёт загрузка. */
    RUNNING,
    /** Завершена без ошибок. */
    DONE,
    /** Завершена с ошибками — хотя бы один день не загрузился. */
    FAILED,
    CANCELLED
}