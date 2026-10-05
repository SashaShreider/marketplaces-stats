package ru.analizer.marketplace.ozon;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import ru.analizer.integration.ozon.OzonApiException;
import ru.analizer.integration.ozon.OzonClient;
import ru.analizer.integration.ozon.OzonProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Транспортный уровень OZON-клиента против локального HTTP-сервера из JDK.
 * Никаких внешних зависимостей и ни одного обращения к api-seller.ozon.ru.
 */
class OzonClientTransportTest {
    /** Подставные реквизиты: тест проверяет заголовки, а не настоящий ключ. */
    private static final ru.analizer.account.domain.MarketplaceCredentials CREDENTIALS =
            new ru.analizer.account.domain.MarketplaceCredentials("1154", "test-key");

    private HttpServer server;
    private String baseUrl;
    private final List<RecordedRequest> recorded = new ArrayList<>();
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger failuresLeft = new AtomicInteger(0);
    private volatile String responseBody = "{}";

    private record RecordedRequest(String method, String path, String clientId, String apiKey, String body) {
    }

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        recorded.add(new RecordedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestHeaders().getFirst("Client-Id"),
                exchange.getRequestHeaders().getFirst("Api-Key"),
                body));

        int currentStatus = failuresLeft.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0
                ? 429
                : status.get();
        byte[] payload = responseBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(currentStatus, payload.length);
        try (var out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }

    private OzonClient client(int maxRetries) {
        OzonProperties properties = new OzonProperties(baseUrl,
                Duration.ofSeconds(2), Duration.ofSeconds(2), maxRetries, Duration.ofMillis(10));
        return new OzonClient(RestClient.builder(), properties);
    }

    @Test
    void sendsCredentialsAndBodyToByDayEndpoint() {
        responseBody = """
                {"accruals":[{"accrual_id":1,"date":"2026-04-10","total_amount":{"amount":"10.5","currency":"RUB"},
                "unit_number":"u-1","accrued_category":"NON_ITEM","posting":null,"item_fees":null,
                "non_item_fee":{"type_id":12,"accrued":{"amount":"10.5","currency":"RUB"}},"container_fees":null}],
                "last_id":""}
                """;

        var page = client(0).getAccrualsByDay(CREDENTIALS, java.time.LocalDate.of(2026, 4, 10), null);

        assertThat(recorded).hasSize(1);
        RecordedRequest request = recorded.getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/v1/finance/accrual/by-day");
        assertThat(request.clientId()).isEqualTo("1154");
        assertThat(request.apiKey()).isEqualTo("test-key");
        // Первый запрос уходит с пустым last_id.
        assertThat(request.body()).contains("\"date\":\"2026-04-10\"");
        assertThat(request.body()).contains("\"last_id\":\"\"");
        assertThat(page.accruals()).hasSize(1);
        assertThat(page.accruals().getFirst().totalAmount()).isEqualByComparingTo("10.5");
        assertThat(page.hasNextPage()).isFalse();
    }

    @Test
    void passesCursorOnSubsequentPage() {
        responseBody = "{\"accruals\":[],\"last_id\":\"next-cursor\"}";

        var page = client(0).getAccrualsByDay(CREDENTIALS, java.time.LocalDate.of(2026, 4, 10), "next-cursor");

        assertThat(recorded.getFirst().body()).contains("\"last_id\":\"next-cursor\"");
        assertThat(page.hasNextPage()).isTrue();
        assertThat(page.lastId()).isEqualTo("next-cursor");
    }

    @Test
    void preservesRawJsonOfEachAccrual() {
        responseBody = """
                {"accruals":[{"accrual_id":777,"date":"2026-04-10","total_amount":{"amount":"-1.25","currency":"RUB"},
                "unit_number":null,"accrued_category":"NON_ITEM","non_item_fee":{"type_id":46,
                "accrued":{"amount":"-1.25","currency":"RUB"}}}],
                "last_id":""}
                """;

        var page = client(0).getAccrualsByDay(CREDENTIALS, java.time.LocalDate.of(2026, 4, 10), null);

        String raw = page.accruals().getFirst().rawJson();
        assertThat(raw).contains("\"accrual_id\":777");
        assertThat(page.accruals().getFirst().unitNumber()).isNull();
    }

    @Test
    void loadsAccrualTypesDictionary() {
        responseBody = """
                {"accrual_types":[{"id":1,"name":"Acquiring","description":"Эквайринг"},
                {"id":74,"name":"StarsMembership","description":"Звёздный товар"}]}
                """;

        var types = client(0).getAccrualTypes(CREDENTIALS);

        assertThat(recorded.getFirst().path()).isEqualTo("/v1/finance/accrual/types");
        assertThat(types.safeAccrualTypes()).hasSize(2);
        assertThat(types.safeAccrualTypes().getFirst().id()).isEqualTo(1);
        assertThat(types.safeAccrualTypes().getFirst().name()).isEqualTo("Acquiring");
    }

    @Test
    void retriesOnTooManyRequests() {
        responseBody = "{\"accruals\":[],\"last_id\":\"\"}";
        failuresLeft.set(2);

        var page = client(3).getAccrualsByDay(CREDENTIALS, java.time.LocalDate.of(2026, 4, 10), null);

        assertThat(recorded).as("два 429, затем успех").hasSize(3);
        assertThat(page.accruals()).isEmpty();
    }

    @Test
    void doesNotRetryBadRequestAndReportsOzonError() {
        status.set(400);
        responseBody = "{\"code\":8,\"message\":\"bad request\",\"details\":[]}";
        failuresLeft.set(0);

        assertThatThrownBy(() -> client(1).getAccrualsByDay(CREDENTIALS, java.time.LocalDate.of(2026, 4, 10), null))
                .isInstanceOf(OzonApiException.class)
                .hasMessageContaining("400")
                .satisfies(e -> {
                    OzonApiException ex = (OzonApiException) e;
                    assertThat(ex.status().value()).isEqualTo(400);
                    assertThat(ex.ozonCode()).isEqualTo(8);
                    assertThat(ex.responseBody()).contains("bad request");
                });
        // 400 — ошибка запроса, а не временная: повторять бессмысленно.
        assertThat(recorded).hasSize(1);
    }

    @Test
    void retriesOnServerErrorThenGivesUp() {
        status.set(500);
        responseBody = "{\"code\":1,\"message\":\"internal\",\"details\":[]}";
        failuresLeft.set(0);

        assertThatThrownBy(() -> client(2).getAccrualsByDay(CREDENTIALS, java.time.LocalDate.of(2026, 4, 10), null))
                .isInstanceOf(OzonApiException.class)
                .hasMessageContaining("500");
        // Одна попытка плюс два повтора.
        assertThat(recorded).hasSize(3);
    }

    @Test
    void doesNotRetryClientErrors() {
        status.set(403);
        responseBody = "{\"code\":7,\"message\":\"forbidden\",\"details\":[]}";

        assertThatThrownBy(() -> client(5).getAccrualsByDay(CREDENTIALS, java.time.LocalDate.of(2026, 4, 10), null))
                .isInstanceOf(OzonApiException.class);
        assertThat(recorded).as("403 не повторяем").hasSize(1);
    }
}
