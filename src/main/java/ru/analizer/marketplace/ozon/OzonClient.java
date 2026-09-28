package ru.analizer.marketplace.ozon;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.analizer.marketplace.AccrualDto;
import ru.analizer.marketplace.AccrualPage;
import ru.analizer.marketplace.ozon.dto.FinanceAccrual;
import ru.analizer.marketplace.ozon.dto.FinanceAccrualByDayRequest;
import ru.analizer.marketplace.ozon.dto.FinanceAccrualTypesResponse;
import ru.analizer.marketplace.ozon.dto.OzonErrorResponse;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Тонкая HTTP-обёртка над двумя методами OZON Seller API.
 * Содержит только транспорт: заголовки, разбор пагинации, ретраи, разбор ошибок.
 * Исходный JSON каждой операции сохраняется рядом с разобранным объектом —
 * без него нельзя было бы переинтерпретировать данные при смене правил аналитики.
 */
@Component
public class OzonClient {

    private static final String PATH_BY_DAY = "/v1/finance/accrual/by-day";
    private static final String PATH_TYPES = "/v1/finance/accrual/types";

    private final RestClient restClient;
    private final OzonProperties properties;
    private final ObjectMapper mapper;

    public OzonClient(RestClient.Builder builder, OzonProperties properties) {
        this.properties = properties;
        this.mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Client-Id", orEmpty(properties.clientId()))
                .defaultHeader("Api-Key", orEmpty(properties.apiKey()))
                .build();
    }

    /**
     * Одна страница начислений за дату.
     *
     * @param date  дата начислений; при непустом {@code lastId} должна передаваться та же дата,
     *              иначе OZON отвечает 400
     * @param lastId курсор следующей страницы, {@code null} или пустая строка — первая страница
     */
    public AccrualPage getAccrualsByDay(LocalDate date, String lastId) {
        FinanceAccrualByDayRequest request = new FinanceAccrualByDayRequest(date.toString(), orEmpty(lastId));

        ObjectNode root = execute("GET " + PATH_BY_DAY, () -> restClient.post()
                .uri(PATH_BY_DAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ObjectNode.class));

        List<AccrualDto> parsed = new ArrayList<>();

        JsonNode accrualsNode = root.get("accruals");
        if (accrualsNode instanceof ArrayNode arrayNode) {
            for (JsonNode node : arrayNode) {
                String rawJson = node.toString();
                FinanceAccrual accrual = mapper.treeToValue(node, FinanceAccrual.class);
                parsed.add(OzonMapper.toAccrualDto(accrual, rawJson));
            }
        }

        JsonNode lastIdNode = root.get("last_id");
        String nextLastId = lastIdNode == null || lastIdNode.isNull() ? "" : lastIdNode.asString();
        return new AccrualPage(parsed, nextLastId);
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

    private Integer parseOzonCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            OzonErrorResponse error = mapper.readValue(body, OzonErrorResponse.class);
            return error.code();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
