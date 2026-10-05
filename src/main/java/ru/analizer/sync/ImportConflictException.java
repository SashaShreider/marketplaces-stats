package ru.analizer.sync;

import java.time.LocalDate;

/**
 * Импорт нельзя запустить, потому что уже идёт другой.
 *
 * <p>Единое исключение с причиной {@link Conflict}, а не три отдельных класса: клиенту
 * нужен один формат ошибки, а различать ситуации удобнее по машиночитаемому полю
 * {@code conflict}, чем по типу исключения на сервере.
 *
 * <p>Отдельное исключение, а не общая ошибка, потому что пользователь должен понять, что
 * нужно дождаться текущей загрузки, а не искать, что сломалось.
 */
public class ImportConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Что именно мешает запуску. Попадает в ответ как поле {@code conflict}. */
    public enum Conflict {

        /** Финансовый импорт за пересекающийся с уже идущим период. */
        OVERLAPPING_PERIOD,

        /** Импорт каталога уже идёт: каталог у аккаунта один. */
        CATALOG_BUSY,

        /**
         * На маркетплейс заведено несколько аккаунтов, а запрос не указывает какой.
         *
         * <p>Молча выбрать один значило бы отдать данные не того продавца, поэтому
         * ситуация считается ошибкой настройки, а не загадкой.
         */
        AMBIGUOUS_ACCOUNT
    }

    private final transient Conflict conflict;
    private final transient Long activeImportId;
    private final transient String activeDescription;
    private final transient LocalDate requestedFrom;
    private final transient LocalDate requestedTo;

    private ImportConflictException(Conflict conflict, String message, String title,
                                    Long activeImportId, String activeDescription,
                                    LocalDate requestedFrom, LocalDate requestedTo) {
        super(message);
        this.conflict = conflict;
        this.activeImportId = activeImportId;
        this.activeDescription = activeDescription;
        this.requestedFrom = requestedFrom;
        this.requestedTo = requestedTo;
    }

    public static ImportConflictException overlappingPeriod(Long activeImportId, String activeDescription,
                                                            LocalDate requestedFrom, LocalDate requestedTo) {
        return new ImportConflictException(
                Conflict.OVERLAPPING_PERIOD,
                "Импорт уже выполняется: " + activeDescription
                        + ". Дождитесь его окончания: повторный импорт пересекающегося периода"
                        + " запрещён, иначе два процесса писали бы одни и те же операции.",
                "Импорт пересекающегося периода уже выполняется",
                activeImportId, activeDescription, requestedFrom, requestedTo);
    }

    public static ImportConflictException catalogBusy(Long activeImportId) {
        return new ImportConflictException(
                Conflict.CATALOG_BUSY,
                "Импорт каталога уже выполняется (задача №" + activeImportId + ")."
                        + " Дождитесь его окончания: два процесса переписывали бы одни и те же товары.",
                "Импорт каталога уже выполняется",
                activeImportId, "задача №" + activeImportId, null, null);
    }

    public static ImportConflictException ambiguousAccount(int accounts, String marketplaceCode) {
        return new ImportConflictException(
                Conflict.AMBIGUOUS_ACCOUNT,
                "На маркетплейс " + marketplaceCode + " заведено " + accounts + " аккаунтов, "
                        + "а запрос не указывает, чей. Выберите один аккаунт или оставьте единственный.",
                "Несколько аккаунтов на одном маркетплейсе",
                null, null, null, null);
    }

    /** Короткий заголовок для поля {@code title} в ответе. */
    public String title() {
        return switch (conflict) {
            case OVERLAPPING_PERIOD -> "Импорт пересекающегося периода уже выполняется";
            case CATALOG_BUSY -> "Импорт каталога уже выполняется";
            case AMBIGUOUS_ACCOUNT -> "Несколько аккаунтов на одном маркетплейсе";
        };
    }

    public Conflict conflict() {
        return conflict;
    }

    public Long activeImportId() {
        return activeImportId;
    }

    public String activeDescription() {
        return activeDescription;
    }

    public LocalDate requestedFrom() {
        return requestedFrom;
    }

    public LocalDate requestedTo() {
        return requestedTo;
    }
}