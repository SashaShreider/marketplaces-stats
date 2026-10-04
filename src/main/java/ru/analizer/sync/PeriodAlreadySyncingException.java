package ru.analizer.sync;

import java.time.LocalDate;

/**
 * По этому аккаунту уже идёт загрузка периода, пересекающегося с запрошенным.
 *
 * <p>Отдельное исключение, а не общая ошибка: пользователь должен понять, что нужно
 * дождаться текущей загрузки, а не искать, что сломалось.
 */
public class PeriodAlreadySyncingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient String activeDescription;
    private final transient LocalDate requestedFrom;
    private final transient LocalDate requestedTo;

    public PeriodAlreadySyncingException(String activeDescription,
                                          LocalDate requestedFrom,
                                          LocalDate requestedTo) {
        super("Загрузка уже выполняется: " + activeDescription
                + ". Дождитесь её окончания — повторный запуск пересекающегося периода запрещён, "
                + "иначе два процесса писали бы одни и те же операции.");
        this.activeDescription = activeDescription;
        this.requestedFrom = requestedFrom;
        this.requestedTo = requestedTo;
    }

    public String getActiveDescription() {
        return activeDescription;
    }

    public LocalDate getRequestedFrom() {
        return requestedFrom;
    }

    public LocalDate getRequestedTo() {
        return requestedTo;
    }
}