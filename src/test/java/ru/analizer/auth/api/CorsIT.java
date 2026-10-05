package ru.analizer.auth.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import ru.analizer.support.AbstractPostgresIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CORS для фронтенда, запущенного отдельно.
 *
 * <p>Контекст поднимается с непустым списком источников намеренно: проверять надо
 * ровно тот случай, ради которого настройка и добавлена. При пустом списке (по
 * умолчанию) ответов CORS нет вообще — это тоже верное поведение, но проверить его
 * таким тестом нельзя, не убрав саму настройку.
 *
 * <p>Отдельно проверяется предварительный запрос: без него не работает ничего, а
 * увидеть его вручную почти невозможно — браузер сообщает «ошибка сети» без кода.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class CorsIT extends AbstractPostgresIntegrationTest {

    private static final String FRONTEND = "http://localhost:5173";
    private static final String STRANGER = "http://attacker.example";

    /** Заголовок, которым клиент повторяет значение куки CSRF. */
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    @DisplayName("Разрешённый источник получает Allow-Credentials, иначе кука не поедет")
    void allowedOriginGetsCredentials() throws Exception {
        HttpResponse<String> response = client.send(withOrigin(FRONTEND).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .as("без заголовка источника браузер выбросит ответ вместе с кукой")
                .contains(FRONTEND);
        // Без этой пары заголовков браузер молча не приложит куку, и фронтенд будет
        // бесконечно получать 401 при заведомо верном пароле.
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials"))
                .as("кука сессии не поедет из другого источника без явного разрешения")
                .contains("true");
    }

    @Test
    @DisplayName("Чужой источник не получает разрешение — только 401 от самого метода")
    void strangerOriginIsNotAllowed() throws Exception {
        HttpResponse<String> response = client.send(withOrigin(STRANGER).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // Метод закрыт, поэтому 401 — это отдельная тема. Здесь важно другое:
        // заголовка разрешения быть не должно ни при каком коде ответа.
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .as("чужому источнику нельзя выдавать разрешение")
                .isEmpty();
        assertThat(response.headers().firstValue("Access-Control-Allow-Credentials"))
                .as("чужому источнику нельзя разрешать куки")
                .isEmpty();
    }

    @Test
    @DisplayName("Предварительный запрос проходит: иначе браузер не отправит основной")
    void preflightPassesBeforeCsrf() throws Exception {
        // DELETE меняет данные и потому уходит сначала с проверкой CORS. Если бы он
        // отклонился как подделка CSRF, браузер не отправил бы сам DELETE и отчёт об
        // ошибке был бы пустым — ровно тот случай, который невозможно отладиить.
        HttpRequest request = HttpRequest.newBuilder().uri(uri("/api/marketplaces/ozon"))
                .header("Origin", FRONTEND)
                .header("Access-Control-Request-Method", "DELETE")
                .header("Access-Control-Request-Headers", CSRF_HEADER_NAME)
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode())
                .as("предварительный запрос должен пройти, а не отклониться проверкой CSRF")
                .isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .contains(FRONTEND);
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods"))
                .as("без списка методов браузер не отправит DELETE")
                .hasValueSatisfying(value -> assertThat(value).contains("DELETE"));
    }

    private HttpRequest.Builder withOrigin(String origin) {
        return HttpRequest.newBuilder().uri(uri("/api/marketplaces")).header("Origin", origin);
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }
}
