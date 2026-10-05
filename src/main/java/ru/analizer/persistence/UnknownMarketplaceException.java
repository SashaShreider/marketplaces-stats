package ru.analizer.persistence;

/**
 * В пути запроса указан маркетплейс, которого в системе нет.
 *
 * <p>Отдельный 404, а не 400: адрес верен, ресурса по нему просто нет. Иначе клиент
 * принял бы опечатку в пути за ошибку своих параметров.
 */
public class UnknownMarketplaceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public UnknownMarketplaceException(String code) {
        super("Неизвестный маркетплейс: " + code
                + ". Доступные: GET /api/marketplaces");
        this.code = code;
    }

    public String code() {
        return code;
    }
}