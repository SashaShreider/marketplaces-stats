package ru.analizer.catalog.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ru.analizer.support.AbstractHttpIntegrationTest;
import ru.analizer.support.FixtureAdapterConfig;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Подсказка товаров для фильтра «Обзора».
 *
 * <p>Проверяется настоящим HTTP-запросом: этот метод читает фронтенд, и его форма —
 * часть договора. Ошибка здесь не падает ни в одном сервисном тесте: страница просто
 * покажет пустой список и будет выглядеть так, будто товаров нет.
 *
 * <p>Тест не импортирует начисления: подсказка отвечает по каталогу, а не по деньгам,
 * и требовать загруженных дней от неё значило бы связать два независимых состояния.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FixtureAdapterConfig.class)
class CatalogApiIT extends AbstractHttpIntegrationTest {

    private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private JsonNode products(String query) throws Exception {
        String path = "/api/accounts/" + accountId() + "/data/products"
                + (query == null ? "" : "?query=" + java.net.URLEncoder.encode(
                        query, java.nio.charset.StandardCharsets.UTF_8));
        HttpResponse<String> response = get(path);
        assertThat(response.statusCode()).as("ответ: %s", response.body()).isEqualTo(200);
        return MAPPER.readTree(response.body());
    }

    @Test
    @DisplayName("Подсказка ищет товары по названию и отдаёт то, что нужно фильтру")
    void findsProductsByName() throws Exception {
        catalogImportService.importProducts(accountId(), null);

        JsonNode found = products("Сурцуков");

        assertThat(found.isArray()).isTrue();
        assertThat(found.size()).isPositive().isLessThanOrEqualTo(20);
        // Название и артикул нужны, чтобы человек узнал товар в списке, а SKU —
        // чтобы подставить его в отчёт параметром skus.
        JsonNode first = found.get(0);
        assertThat(first.get("sku").asLong()).isPositive();
        assertThat(first.get("name").asString()).contains("Сурцуков");
        assertThat(first.has("offerId")).isTrue();
    }

    @Test
    @DisplayName("Без запроса подсказка отдаёт начало списка, а не пустоту")
    void emptyQueryReturnsFirstPage() throws Exception {
        catalogImportService.importProducts(accountId(), null);

        // Раскрытый список без ввода должен что-то показывать: пустой ответ выглядел
        // бы как «товаров нет», хотя они есть.
        assertThat(products(null).size()).isPositive();
        assertThat(products("").size()).isPositive();
    }

    @Test
    @DisplayName("Найденный SKU действительно сужает отчёт")
    void suggestedSkuWorksAsReportFilter() throws Exception {
        catalogImportService.importProducts(accountId(), null);

        long sku = products("Сурцуков").get(0).get("sku").asLong();

        // Подсказка и отчёт обязаны говорить на одном языке: если бы подсказка отдавала
        // не тот номер, отбор по выбранному товару молча показал бы нули.
        HttpResponse<String> report = get("/api/accounts/" + accountId()
                + "/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10&skus=" + sku);
        assertThat(report.statusCode()).as("ответ: %s", report.body()).isEqualTo(200);
        assertThat(MAPPER.readTree(report.body()).get("filter").asString())
                .isEqualTo("SKU " + sku);
    }

    @Test
    @DisplayName("Ограничение размера приводится к разумному диапазону, а не роняет запрос")
    void limitIsBounded() throws Exception {
        catalogImportService.importProducts(accountId(), null);

        for (String limit : new String[]{"0", "-5", "1000"}) {
            HttpResponse<String> response = get("/api/accounts/" + accountId()
                    + "/data/products?limit=" + limit);
            assertThat(response.statusCode())
                    .as("limit=%s: %s", limit, response.body())
                    .isEqualTo(200);
            assertThat(MAPPER.readTree(response.body()).size())
                    .as("limit=%s не должен отдавать больше 50 строк", limit)
                    .isLessThanOrEqualTo(50);
        }
    }

    @Test
    @DisplayName("Пустой каталог — пустой список, а не ошибка")
    void emptyCatalogReturnsEmptyList() throws Exception {
        JsonNode found = products("книга");

        assertThat(found.isArray()).isTrue();
        assertThat(found.size()).isZero();
    }
}
