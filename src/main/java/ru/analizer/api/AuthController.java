package ru.analizer.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.analizer.persistence.entity.AppUser;

import java.util.Map;

/**
 * Регистрация, вход и состояние сессии.
 *
 * <p>Ни один метод не отдаёт пароль и хэш. {@code GET /api/auth/me} существует
 * отдельной целью: фронтенд вызывает его при старте, получает 200 с пользователем
 * или 401, и по этому решает — показать приложение или форму входа.
 *
 * <p>Серверных редиректов здесь нет намеренно: бэкенд отвечает кодами, а решает,
 * какую страницу показать, фронтенд.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(AuthService authService,
                          AuthenticationManager authenticationManager,
                          SecurityContextRepository securityContextRepository) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * POST /api/auth/register
     *
     * <p>Сессия сразу не создаётся: пусть пользователь сначала подтвердит, что
     * понимает, куда попал, и войдёт. Так первый экран после регистрации —
     * предсказуемая форма входа.
     *
     * @return 201 с логином и отображаемым именем
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        AppUser user = authService.register(
                request.login(), request.password(), request.displayName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CurrentUserInfo(user.getLogin(), user.getDisplayName()));
    }

    /**
     * POST /api/auth/login
     *
     * <p>Сессия создаётся здесь: ответ несёт {@code Set-Cookie}. Неверные данные
     * дают 401, а не 400 — иначе клиент отличил бы «нет такого логина» от «не тот
     * пароль» и перебор становился бы проще.
     *
     * <p>Проверку пароля делает {@link AuthenticationManager}, тот же, что и обычная
     * форма входа. Свою проверку через репозиторий писать не надо: тогда появятся две
     * реализации аутентификации, которые со временем разойдутся — например, одна
     * начнёт учитывать блокировку, а другая нет.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request,
                                   HttpServletRequest httpRequest,
                                   HttpServletResponse httpResponse) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.login(), request.password()));

        // Сессия до входа уничтожается: иначе злоумышленник, подсунувший свою
        // JSESSIONID, продолжил бы пользоваться ею уже как авторизованной.
        HttpSession existing = httpRequest.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // Явное сохранение контекста в сессии: без него запрос завершился бы, а
        // пользователь остался бы анонимом до следующего обращения.
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        AppUser user = authService.requireByLogin(authentication.getName());
        return ResponseEntity.ok(new CurrentUserInfo(user.getLogin(), user.getDisplayName()));
    }

    /** POST /api/auth/logout — сессия уничтожается. */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/auth/me
     *
     * <p>Отдельная проверка сессии не нужна: метод закрыт фильтром безопасности, и без
     * сессии до контроллера дело не доходит — ответ 401 даёт фильтр.
     *
     * @return 200 с пользователем либо 401 без сессии
     */
    @GetMapping("/me")
    public CurrentUserInfo me() {
        return currentUserInfo();
    }

    /**
     * GET /api/auth/csrf
     *
     * <p>Хранит токен и возвращает его. Фронтенд может и не вызывать этот метод:
     * токен уже лежит в куке XSRF-TOKEN, которую JavaScript прочитать может.
     * Метент нужен, когда кука недоступна — например, при запросе из теста.
     */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of(
                "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName(),
                "token", token.getToken());
    }

    private CurrentUserInfo currentUserInfo() {
        AppUser user = authService.requireByLogin(ru.analizer.persistence.CurrentUser.login());
        return new CurrentUserInfo(user.getLogin(), user.getDisplayName());
    }

    /** @param login       логин, как введён при регистрации
     *  @param displayName отображаемое имя; никогда не пустое */
    public record CurrentUserInfo(String login, String displayName) {
    }

    /** @param login логин, 3–64 символа, без пробелов
     *  @param password пароль, минимум 8 символов */
    public record RegisterRequest(String login, String password, String displayName) {
    }

    /** Тело запроса входа. */
    public record LoginRequest(String login, String password) {
    }
}