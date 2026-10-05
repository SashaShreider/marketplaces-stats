package ru.analizer.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.analizer.auth.domain.AuthService;
import ru.analizer.integration.CredentialsRejectedException;
import ru.analizer.integration.ozon.OzonApiException;
import ru.analizer.sync.domain.ImportConflictException;

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

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Некорректные параметры запроса");
        return problem;
    }

/**
 * Состояние данных, а не сбой сервера: аккаунт известен, но ничего не импортировано.
 * Отдавать это как 500 с пустым телом значит заставить клиента гадать.
 */
@ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleDataNotReady(IllegalStateException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Данные ещё не готовы");
        problem.setType(URI.create("urn:analizer:error:data-not-ready"));
        return problem;
    }

/**
     * Импорт нельзя запустить: уже идёт другой, либо на маркетплейсе несколько аккаунтов.
     *
     * <p>Отдельный 409 с машиночитаемым полем {@code conflict}: клиенту нужно знать, что
     * дождаться, а не что он сделал что-то не так. Значение {@code conflict} позволяет
     * отличить случаи, не разбирая текст сообщения.
     */
    @ExceptionHandler(ImportConflictException.class)
    public ProblemDetail handleImportConflict(ImportConflictException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle(e.title());
        problem.setType(URI.create("urn:analizer:error:import-conflict"));
        problem.setProperty("conflict", e.conflict().name());
        if (e.activeImportId() != null) {
            problem.setProperty("activeImportId", e.activeImportId());
        }
        if (e.activeDescription() != null) {
            problem.setProperty("activeImport", e.activeDescription());
        }
        if (e.requestedFrom() != null) {
            problem.setProperty("requestedFrom", e.requestedFrom());
        }
        if (e.requestedTo() != null) {
            problem.setProperty("requestedTo", e.requestedTo());
        }
        return problem;
    }

@ExceptionHandler(ru.analizer.web.UnknownMarketplaceException.class)
    public ProblemDetail handleUnknownMarketplace(ru.analizer.web.UnknownMarketplaceException e) {
        // Отдельный 404, а не 400: адрес верен, ресурса по нему просто нет.
        // Иначе клиент принял бы опечатку в пути за ошибку своих параметров.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Неизвестный маркетплейс");
        problem.setType(URI.create("urn:analizer:error:unknown-marketplace"));
        problem.setProperty("marketplace", e.code());
        return problem;
    }

    /**
     * Неверный логин или пароль.
     *
     * <p>Отдельный 401: клиенту нужно знать, что показать форму входа, а не
     * сообщение о неверных параметрах.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException e) {
        // Текст не различает «нет логина» и «не тот пароль» — по разнице можно было бы
        // перебирать существующие логины.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNAUTHORIZED, "Неверный логин или пароль");
        problem.setTitle("Не удалось войти");
        problem.setType(URI.create("urn:analizer:error:bad-credentials"));
        return problem;
    }

    /** Логин уже занят. Отдельный 409: повтор регистрации, а не ошибка запроса. */
    @ExceptionHandler(AuthService.LoginTakenException.class)
    public ProblemDetail handleLoginTaken(AuthService.LoginTakenException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Логин занят");
        problem.setType(URI.create("urn:analizer:error:login-taken"));
        return problem;
    }

    /**
     * Маркетплейс не подключён.
     *
     * <p>409, а не 404: адрес верен, но состояние не позволяет выполнить операцию.
     * Отдельный тип нужен, чтобы фронтенд показал «подключите маркетплейс», а не
     * «что-то сломалось».
     */
    @ExceptionHandler(NotConnectedException.class)
    public ProblemDetail handleNotConnected(NotConnectedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
        problem.setTitle("Маркетплейс не подключён");
        problem.setType(URI.create("urn:analizer:error:not-connected"));
        return problem;
    }

    /**
     * Реквизиты отвергнуты маркетплейсом.
     *
     * <p>400: пользователь ввёл неверные данные. Внутренности ошибки маркетплейса
     * в ответ не попадают — они раскрывают детали реализации, о которых клиенту
     * знать незачем.
     */
    @ExceptionHandler(CredentialsRejectedException.class)
    public ProblemDetail handleRejectedCredentials(CredentialsRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Маркетплейс отверг реквизиты");
        problem.setType(URI.create("urn:analizer:error:credentials-rejected"));
        problem.setProperty("remoteStatus", e.remoteStatus());
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
