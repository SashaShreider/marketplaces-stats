package ru.analizer.integration;

/**
 * Реквизиты отвергнуты маркетплейсом.
 *
 * <p>Отдельное исключение, а не общая ошибка: пользователь должен понять, что ввёл
 * неверный ключ, а не искать, что сломалось в приложении.
 */
public class CredentialsRejectedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int remoteStatus;

    public CredentialsRejectedException(String marketplaceCode, int remoteStatus) {
        super("Маркетплейс " + marketplaceCode + " отверг реквизиты (HTTP " + remoteStatus
                + "). Проверьте client_id и api_key.");
        this.remoteStatus = remoteStatus;
    }

    public int remoteStatus() {
        return remoteStatus;
    }
}