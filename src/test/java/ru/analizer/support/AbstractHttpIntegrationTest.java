package ru.analizer.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * База для тестов, ходящих в приложение по настоящему HTTP.
 *
 * <p>Отделён от {@link AbstractPostgresIntegrationTest}, потому что поднимает сервер:
 * тесты сервисов работают в режиме MOCK, и порт там не существует.
 *
 * <p>Куки ведутся настоящим {@link CookieManager}, а не собираются вручную. Разница
 * существенна: CSRF проверяется по куке, а в заголовок клиент повторяет её значение.
 * Собранный вручную заголовок легко забыть дополнить, и тест упадёт с 403, которого
 * в жизни не бывает.
 *
 * <p>Вход выполняется реальными запросами, а не подстановкой пользователя в контекст.
 * Так проверяется весь путь: выдача куки, защита от CSRF, сверка пароля. Подменив
 * пользователя, тест прошёл бы там, где настоящий запрос получил бы 401, и проверял бы
 * несуществующий сценарий.
 */
public abstract class AbstractHttpIntegrationTest extends AbstractPostgresIntegrationTest {

    /** Имя куки с токеном: клиент читает её и повторяет значение в заголовке. */
    protected static final String CSRF_COOKIE = "XSRF-TOKEN";

    /** Заголовок, которым клиент повторяет значение куки. */
    protected static final String CSRF_HEADER = "X-XSRF-TOKEN";

    /** Значение CSRF-токена, прочитанное из куки. */
    protected String csrfToken;

    /** Кука сессии {@code JSESSIONID=...}; заполняется при входе. */
    protected String sessionCookie;

    /** Клиент с настоящим хранилищем кук — работает как браузер. */
    private HttpClient client;

    @LocalServerPort
    protected int port;

    @BeforeEach
    void signInBeforeEach() throws IOException, InterruptedException {
        // Хранилище кук создаётся заново на каждый тест: иначе сессия от предыдущего
        // теста пережила бы очистку базы и запросы шли бы от удалённого пользователя.
        client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .build();
        readCsrfToken();
        signIn();
    }

    /**
     * Входит под тестовым пользователем.
     *
     * <p>Пользователя заводит очистка базы с настоящим BCrypt-хэшем, поэтому здесь
     * только вход. Регистрация проверяется отдельно: смешивать её с каждым HTTP-тестом
     * незачем — тесты проверяют API аналитики, а не регистрацию.
     */
    protected void signIn() throws IOException, InterruptedException {
        // Вход публичен, но CSRF защищает и его: без заголовка запрос отклонился бы,
        // и непонятно было бы, что именно сломано.
        HttpResponse<String> login = postPublic("/api/auth/login", """
                {"login":"%s","password":"%s"}
                """.formatted(LOGIN, PASSWORD));
        assertThat(login.statusCode())
                .as("вход вернул %s: %s", login.statusCode(), login.body())
                .isEqualTo(200);

        sessionCookie = findCookie(login.headers(), "JSESSIONID");
        assertThat(sessionCookie).as("вход обязан выдать куку сессии").isNotBlank();
    }

    /** Читает токен из куки — так же, как это делает фронтенд. */
    private void readCsrfToken() throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder().uri(URI.create(url("/api/auth/csrf"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode())
                .as("получение CSRF-токена вернуло %s", response.statusCode())
                .isEqualTo(200);

        // Тело не разбираем: значение есть и в куке, и в JSON, а куку читает клиент
        // так же, как это делает настояльный фронтенд.
        String cookie = findCookie(response.headers(), CSRF_COOKIE);
        assertThat(cookie).as("метод CSRF обязан выставить куку %s", CSRF_COOKIE).isNotBlank();
        csrfToken = cookie.substring(cookie.indexOf('=') + 1);
        assertThat(csrfToken).as("значение куки CSRF не должно быть пустым").isNotBlank();
    }

    /** GET текущим пользователем: куки идут через хранилище клиента. */
    protected HttpResponse<String> get(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** POST без тела текущим пользователем, с заголовком CSRF. */
    protected HttpResponse<String> post(String path) throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path)))
                        .header(CSRF_HEADER, csrfToken)
                        .POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** POST c JSON-телом текущим пользователем, с заголовком CSRF. */
    protected HttpResponse<String> postJson(String path, String body)
            throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path)))
                        .header("Content-Type", "application/json")
                        .header(CSRF_HEADER, csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** PUT c JSON-телом и заголовком CSRF: подключение реквизитов. */
    protected HttpResponse<String> putJson(String path, String body)
            throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path)))
                        .header("Content-Type", "application/json")
                        .header(CSRF_HEADER, csrfToken)
                        .PUT(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Запрос вообще без кук — для проверки, что защищённый метод отвечает 401.
     *
     * <p>Отдельный клиент: у общего уже есть сессия, и запрос с ней проверил бы не то.
     */
    protected HttpResponse<String> getAnonymous(String path) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    /** Значение куки {@code name} из ответа вместе с {@code name=}, либо пустая строка. */
    protected static String findCookie(HttpHeaders headers, String name) {
        return headers.allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .findFirst()
                .orElse("");
    }

    /** POST без сессии: отдельный клиент, без заголовка CSRF. */
    protected HttpResponse<String> postPublic(String path, String body)
            throws IOException, InterruptedException {
        return client.send(
                HttpRequest.newBuilder().uri(URI.create(url(path)))
                        .header("Content-Type", "application/json")
                        .header(CSRF_HEADER, csrfToken == null ? "" : csrfToken)
                        .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
}