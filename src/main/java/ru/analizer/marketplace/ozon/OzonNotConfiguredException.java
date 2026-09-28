package ru.analizer.marketplace.ozon;

/**
 * Реквизиты доступа не заданы — синхронизация не может быть выполнена.
 */
public class OzonNotConfiguredException extends RuntimeException {

    public OzonNotConfiguredException() {
        super("OZON_CLIENT_ID / OZON_API_KEY не заданы. Проверьте переменные окружения.");
    }
}
