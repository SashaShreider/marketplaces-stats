package ru.analizer.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Регистрация, вход, выход и защита закрытых методов.
 *
 * <p>Проверяется настоящим HTTP без сессии: подменить пользователя в контексте и
 * объявить поведение проверенным было бы самообманом — так тест прошёл бы там, где
 * реальный клиент получил бы 401.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiIT extends AbstractPostgresIntegrationTest {

    @LocalServerPort
    int port;

    /** Клиент без кук: так выглядит первый визит гостя. */
    private final HttpClient guest = HttpClient.newHttpClient();

    @Test
    @DisplayName("Гость не достаёт ни одного закрытого метода — 401, а не редирект")
    void anonymousGets401InsteadOfRedirect() throws Exception {
        for (String path : new String[]{
                "/api/marketplaces",
                "/api/marketplaces/ozon/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10",
                "/api/marketplaces/ozon/data/coverage?dateFrom=2026-04-10&dateTo=2026-04-10",
                "/api/auth/me"}) {

            HttpResponse<String> response = get(path);

            assertThat(response.statusCode())
                    .as("метод %s обязан отвечать 401 анонимному клиенту", path)
                    .isEqualTo(401);
            // Серверных редиректов здесь нет вовсе: страницу выбирает фронтенд.
            assertThat(response.headers().firstValue("Location"))
                    .as("редирект на страницу входа должен отсутствовать")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("Регистрация, вход и чтение себя: полный путь нового пользователя")
    void registerLoginAndReadSelf() throws Exception {

        HttpResponse<String> registered = postJson("/api/auth/register", """
                {"login":"newcomer","password":"first-password","displayName":"Новичок"}
                """);
        assertThat(registered.statusCode()).as("регистрация: %s", registered.body()).isEqualTo(201);
        assertThat(registered.body()).contains("\"login\":\"newcomer\"");

        HttpResponse<String> login = postJson("/api/auth/login", """
                {"login":"newcomer","password":"first-password"}
                """);
        assertThat(login.statusCode()).as("вход: %s", login.body()).isEqualTo(200);

        String session = cookie(login, "JSESSIONID");
        assertThat(session).as("вход обязан выдать куку сессии").isNotBlank();

        HttpResponse<String> me = get("/api/auth/me", session);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body())
                .contains("\"login\":\"newcomer\"")
                .doesNotContain("first-password")
                .doesNotContain("passwordHash");
    }

    @Test
    @DisplayName("Повторная регистрация того же логина — 409, а не 500")
    void duplicateLoginIsConflict() throws Exception {

        HttpResponse<String> first = postJson("/api/auth/register", """
                {"login":"twice","password":"first-password","displayName":"Первый"}
                """);
        assertThat(first.statusCode()).isEqualTo(201);

        HttpResponse<String> second = postJson("/api/auth/register", """
                {"login":"twice","password":"other-password","displayName":"Второй"}
                """);

        assertThat(second.statusCode()).isEqualTo(409);
        assertThat(second.body()).contains("login-taken");
    }

    @Test
    @DisplayName("Короткий пароль отвергается на регистрации, а не проходит мимо проверки")
    void shortPasswordIsRejected() throws Exception {
        HttpResponse<String> response = postJson("/api/auth/register", """
                {"login":"shorty","password":"123","displayName":"Короткий"}
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("8");
    }

    @Test
    @DisplayName("Неверный пароль даёт 401 и не выдаёт сессию")
    void wrongPasswordIsUnauthorized() throws Exception {
        postJson("/api/auth/register", """
                {"login":"careful","password":"right-password","displayName":"Осторожный"}
                """);

        HttpResponse<String> response = postJson("/api/auth/login", """
                {"login":"careful","password":"wrong-password"}
                """);

        assertThat(response.statusCode())
                .as("неверный пароль — 401, а не 400: иначе клиент отличил бы «нет логина» "
                        + "от «не тот пароль» и перебор упрощался бы")
                .isEqualTo(401);
        assertThat(response.body())
                // Одинаковый текст для «нет логина» и «не тот пароль».
                .contains("Неверный логин или пароль");
        assertThat(cookie(response, "JSESSIONID")).isEmpty();
    }

    @Test
    @DisplayName("Метод без CSRF-токена отклоняется")
    void postWithoutCsrfIsRejected() throws Exception {
        // Ни куки, ни заголовка: так выглядит запрос, который кто-то отправил из
        // чужого сайта, и именно его CSRF-токен и не даёт пройти.
        HttpResponse<String> response = guest.send(
                HttpRequest.newBuilder().uri(URI.create(url("/api/auth/register")))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"login":"nocsrf","password":"first-password","displayName":"Без токена"}
                                """)).build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isIn(401, 403);
    }

    @Test
    @DisplayName("Выход уничтожает сессию: после него закрытые методы снова недоступны")
    void logoutDestroysSession() throws Exception {
        postJson("/api/auth/register", """
                {"login":"leaver","password":"first-password","displayName":"Уходящий"}
                """);
        String session = cookie(postJson("/api/auth/login", """
                {"login":"leaver","password":"first-password"}
                """), "JSESSIONID");

        assertThat(get("/api/auth/me", session).statusCode()).isEqualTo(200);

        HttpResponse<String> logout = postJson("/api/auth/logout", "", session);
        assertThat(logout.statusCode()).isEqualTo(204);

        assertThat(get("/api/auth/me", session).statusCode())
                .as("старая кука после выхода больше не должна работать")
                .isEqualTo(401);
    }

    /**
     * Кука {@code XSRF-TOKEN} целиком: источник истины при проверке CSRF.
 *
     * <p>Клиент обязан слать и куку, и заголовок — ровно так же, как браузер. Токен
     * проверяется по куке, а заголовок лишь повторяет её значение, поэтому запрос с
     * одним заголовком был бы отклонён. Такой отказ невозможен в жизни, и проверять
     * его здесь незачем.
     */
    private String csrfCookie() throws Exception {
        String cookie = cookie(get("/api/auth/csrf"), "XSRF-TOKEN");
        assertThat(cookie).as("метод CSRF обязан выставить куку XSRF-TOKEN").isNotBlank();
        return cookie;
    }

    private HttpResponse<String> get(String path) throws Exception {
        return guest.send(HttpRequest.newBuilder().uri(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String sessionCookie) throws Exception {
        return guest.send(HttpRequest.newBuilder().uri(URI.create(url(path)))
                        .header("Cookie", sessionCookie).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** POST без сессии — регистрация, вход, проверки отказа. */
    private HttpResponse<String> postJson(String path, String body) throws Exception {
        return postJson(path, body, null);
    }

    private HttpResponse<String> postJson(String path, String body, String session)
            throws Exception {
        // Каждый запрос заводит свою пару кук и заголовка: гость без состояния между
        // вызовами, иначе тесты зависели бы от порядка выполнения.
        String cookie = csrfCookie();
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .header("Cookie", cookie)
                .header("X-XSRF-TOKEN", cookie.substring("XSRF-TOKEN=".length()));
        if (session != null) {
            request.header("Cookie", cookie + "; " + session);
        }
        return guest.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static String cookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .findFirst()
                .orElse("");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}