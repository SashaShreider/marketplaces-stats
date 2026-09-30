package ru.analizer.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.analizer.marketplace.ozon.OzonApiException;
import ru.analizer.marketplace.ozon.OzonNotConfiguredException;

import java.net.URI;

/**
 * Единый формат ошибок API.
 *
 * <p>Важно различать «операций не было» и «операции получить не удалось»: второй случай
 * не должен выглядеть как нулевой результат, иначе молчаливый пропуск данных Later
 * посчитают как отсутствие начислений.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Реквизиты OZON не заданы — это состояние конфигурации, а не ошибка запроса. */
    @ExceptionHandler(OzonNotConfiguredException.class)
    public ProblemDetail handleNotConfigured(OzonNotConfiguredException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setTitle("OZON API не сконфигурирован");
        problem.setType(URI.create("urn:analizer:error:ozon-not-configured"));
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Некорректные параметры запроса");
        return problem;
    }

    /**
     * Состояние данных, а не сбой сервера: аккаунт известен, но ещё не синхронизирован.
     * Отдавать это как 500 с пустым телом значит заставить клиента гадать.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleDataNotReady(IllegalStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Данные ещё не готовы");
        problem.setType(URI.create("urn:analizer:error:data-not-ready"));
        return problem;
    }

    @ExceptionHandler(OzonApiException.class)
    public ProblemDetail handleOzonApi(OzonApiException e) {
        log.error("Ошибка OZON API: {}", e.getMessage(), e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setTitle("OZON API вернул ошибку");
        problem.setType(URI.create("urn:analizer:error:ozon-api"));
        problem.setProperty("ozonStatus", e.status() == null ? null : e.status().value());
        problem.setProperty("ozonCode", e.ozonCode());
        return problem;
    }
}
