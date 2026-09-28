package ru.analizer.marketplace.ozon;

import org.springframework.http.HttpStatusCode;

/**
 * Ошибка ответа OZON. Фатальна для текущего запроса, но не обязательно для всей синхронизации.
 */
public class OzonApiException extends RuntimeException {

    private final HttpStatusCode status;
    private final Integer ozonCode;
    private final String responseBody;

    public OzonApiException(String message, HttpStatusCode status, Integer ozonCode, String responseBody) {
        super(message);
        this.status = status;
        this.ozonCode = ozonCode;
        this.responseBody = responseBody;
    }

    public HttpStatusCode status() {
        return status;
    }

    public Integer ozonCode() {
        return ozonCode;
    }

    public String responseBody() {
        return responseBody;
    }
}
