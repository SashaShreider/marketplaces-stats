package ru.analizer.integration.ozon;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.analizer.account.domain.MarketplaceCredentials;
import ru.analizer.integration.model.AccrualDto;
import ru.analizer.integration.model.AccrualPage;
import ru.analizer.integration.model.CatalogPage;
import ru.analizer.integration.model.ProductEntry;
import ru.analizer.integration.ozon.dto.catalog.ProductAttributesRequest;
import ru.analizer.integration.ozon.dto.catalog.ProductInfoV4;
import ru.analizer.integration.ozon.dto.finance.FinanceAccrual;
import ru.analizer.integration.ozon.dto.finance.FinanceAccrualByDayRequest;
import ru.analizer.integration.ozon.dto.finance.FinanceAccrualTypesResponse;
import ru.analizer.integration.ozon.dto.finance.OzonErrorResponse;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
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
    private static final String PATH_PRODUCT_ATTRIBUTES = "/v4/product/info/attributes";

    private final RestClient.Builder restClientBuilder;
    private final OzonProperties properties;
    private final ObjectMapper mapper;

    public OzonClient(RestClient.Builder builder, OzonProperties properties) {
        this.properties = properties;
        this.mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        this.restClientBuilder = builder;
    }

    /**
     * HTTP-клиент под конкретные реквизиты.
     *
     * <p>Раньше реквизиты задавались один раз в конструкторе бина, и это работало,
     * пока аккаунт был единственным. Теперь у каждого пользователя свои ключи, поэтому
     * клиент собирается на каждый вызов.
     *
     * <p>Затраты на сборку ничтожны: {@code RestClient} — лёгкая обёртка над общим
     * {@code HttpClient}, а не новое соединение. Зато состояние не общее: два
     * пользователя не могут перепутать ключи.
     */
    private RestClient clientFor(MarketplaceCredentials credentials) {
        return restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Client-Id", credentials.clientId())
                .defaultHeader("Api-Key", credentials.apiKey())
                .build();
    }

    /**
     * Одна страница начислений за дату.
     *
     * @param date  дата начислений; при непустом {@code lastId} должна передаваться та же дата,
     *              иначе OZON отвечает 400
     * @param lastId курсор следующей страницы, {@code null} или пустая строка — первая страница
     */
    public AccrualPage getAccrualsByDay(MarketplaceCredentials credentials, LocalDate date, String lastId) {
        FinanceAccrualByDayRequest request = new FinanceAccrualByDayRequest(date.toString(), orEmpty(lastId));

        RestClient restClient = clientFor(credentials);
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

    public FinanceAccrualTypesResponse getAccrualTypes(MarketplaceCredentials credentials) {
        return execute("GET " + PATH_TYPES, () -> clientFor(credentials).post()
                .uri(PATH_TYPES)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{}")
                .retrieve()
                .body(FinanceAccrualTypesResponse.class));
    }

    /**
     * Страница характеристик товаров.
     *
     * <p>Курсор {@code last_id} возвращается как есть и непустым даже на последней
     * странице, поэтому решать, есть ли следующая страница, должен вызывающий —
     * см. {@link ru.analizer.integration.CatalogPager}.
     *
     * <p>Исходный JSON каждого товара сохраняется рядом с разобранным: без него
     * неизвестный атрибут пришлось бы угадывать, а переинтерпретировать данные
     * при смене правил было бы нечем.
     */
    public CatalogPage getProductAttributes(MarketplaceCredentials credentials, ProductAttributesRequest request) {
        RestClient restClient = clientFor(credentials);
        ObjectNode root = execute("POST " + PATH_PRODUCT_ATTRIBUTES, () -> restClient.post()
                .uri(PATH_PRODUCT_ATTRIBUTES)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ObjectNode.class));

        List<ProductEntry> products = new ArrayList<>();
        JsonNode resultNode = root.get("result");
        if (resultNode instanceof ArrayNode arrayNode) {
            for (JsonNode node : arrayNode) {
                ProductInfoV4 product = mapper.treeToValue(node, ProductInfoV4.class);
                if (product.sku() == null) {
                    // Без sku товар не связать с начислениями: в отчёте по товарам
                    // такой строкой нечего было бы показать.
                    continue;
                }
                products.add(OzonProductMapper.toProductEntry(product, node.toString()));
            }
        }

        JsonNode totalNode = root.get("total");
        JsonNode lastIdNode = root.get("last_id");
        int total = totalNode == null || totalNode.isNull() ? 0 : totalNode.asInt();
        String lastId = lastIdNode == null || lastIdNode.isNull() ? "" : lastIdNode.asString();
        return new CatalogPage(products, total, lastId);
    }

    private <T> T execute(String operation, Supplier<T> call) {
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
