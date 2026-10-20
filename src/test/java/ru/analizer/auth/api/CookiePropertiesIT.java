package ru.analizer.auth.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ru.analizer.support.AbstractPostgresIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Куки выставляются с теми же параметрами, что и настроено по умолчанию.
 *
 * <p>Значение Secure по умолчанию выключено, и это сделано сознательно: иначе кука
 * не поедет на {@code http://localhost:8080}, и каждый запрос будет давать 401 при
 * 200 на получение самой куки. SameSite по умолчанию — Lax. Вариант с HTTPS
 * настраивается переменными COOKIE_SAME_SITE/COOKIE_SECURE и отдельным контекстом
 * здесь не проверяется.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CookiePropertiesIT extends AbstractPostgresIntegrationTest {

    private static final String CSRF_COOKIE = "XSRF-TOKEN";

    @LocalServerPort
    int port;

    @Test
    @DisplayName("Кука CSRF получает SameSite по умолчанию и остаётся читаемой для JS")
    void csrfCookieUsesConfiguredSameSite() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI.create(url("/api/auth/csrf"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        String setCookie = findSetCookie(response, CSRF_COOKIE);

        assertThat(setCookie)
                .as("кука %s обязана быть выставлена", CSRF_COOKIE)
                .isNotBlank();
        assertThat(setCookie)
                .as("SameSite=%s обязателен, иначе браузер отбросит куку в ответе на запись",
                        "Lax")
                .containsIgnoringCase("SameSite=Lax");
        // HttpOnly в этой куке недопустим: значение читает JavaScript фронтенда, чтобы
        // повторить его в заголовке.
        assertThat(setCookie).as("кука CSRF обязана читаться из JavaScript").doesNotContain("HttpOnly");
    }

    @Test
    @DisplayName("Без HTTPS флаг Secure не выставляется — иначе кука просто не поедет")
    void secureFlagIsNotSetOnHttp() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(URI.create(url("/api/auth/csrf"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(findSetCookie(response, CSRF_COOKIE))
                .as("Secure на http://localhost приводит к вечной потере сессии")
                .doesNotContain("Secure");
    }

    /** Значение куки вместе со всеми её атрибутами, а не только «name=value». */
    private static String findSetCookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .findFirst()
                .orElse("");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
