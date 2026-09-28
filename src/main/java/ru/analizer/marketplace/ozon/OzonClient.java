package ru.analizer.marketplace.ozon;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.analizer.marketplace.ozon.dto.FinanceAccrualByDayRequest;
import ru.analizer.marketplace.ozon.dto.FinanceAccrualByDayResponse;
import ru.analizer.marketplace.ozon.dto.FinanceAccrualTypesResponse;
import ru.analizer.marketplace.ozon.dto.OzonErrorResponse;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Тонкая HTTP-обёртка над двумя методами OZON Seller API.
 * Содержит только транспорт: заголовки, десериализацию, разбор ошибок и ретраи.
 * Бизнес-логики здесь нет.
 */
@Component
public class OzonClient {

    private static final String PATH_BY_DAY = "/v1/finance/accrual/by-day";
    private static final String PATH_TYPES = "/v1/finance/accrual/types";

    private final RestClient restClient;
    private final OzonProperties properties;

    public OzonClient(RestClient.Builder builder, OzonProperties properties) {
        this.properties = properties;
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Client-Id", properties.clientId() == null ? "" : properties.clientId())
                .defaultHeader("Api-Key", properties.apiKey() == null ? "" : properties.apiKey())
                .build();
    }

    public FinanceAccrualByDayResponse getAccrualsByDay(FinanceAccrualByDayRequest request) {
        return execute("GET " + PATH_BY_DAY, () -> restClient.post()
                .uri(PATH_BY_DAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(FinanceAccrualByDayResponse.class));
    }

    public FinanceAccrualTypesResponse getAccrualTypes() {
        return execute("GET " + PATH_TYPES, () -> restClient.post()
                .uri(PATH_TYPES)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(FinanceAccrualTypesResponse.class));
    }

    private <T> T execute(String operation, Supplier<T> call) {
        if (!properties.isConfigured()) {
            throw new OzonNotConfiguredException();
        }
        int attempt = 0;
        while (true) {
            try {
                return call.get();
            } catch (RestClientResponseException e) {
                HttpStatusCode status = e.getStatusCode();
                if (isRetryable(status) && attempt < properties.maxRetries()) {
                    backoff(attempt++);
                    continue;
                }
                throw new OzonApiException(
                        "OZON " + operation + " -> " + status.value(),
                        status,
                        parseOzonCode(e.getResponseBodyAsString()),
                        e.getResponseBodyAsString());
            } catch (OzonApiException e) {
                throw e;
            } catch (RuntimeException e) {
                if (attempt < properties.maxRetries()) {
                    backoff(attempt++);
                    continue;
                }
                throw e;
            }
        }
    }

    private static boolean isRetryable(HttpStatusCode status) {
        int code = status.value();
        return code == 429 || code == 408 || code >= 500;
    }

    private void backoff(int attempt) {
        Duration base = properties.retryBackoff();
        long delayMillis = base.toMillis() * (1L << Math.min(attempt, 5));
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, delayMillis / 4));
        try {
            Thread.sleep(delayMillis + jitter);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Прервано во время ожидания перед повтором запроса к OZON", e);
        }
    }

    private static Integer parseOzonCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            OzonErrorResponse error = MAPPER.readValue(body, OzonErrorResponse.class);
            return error.code();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();
}
