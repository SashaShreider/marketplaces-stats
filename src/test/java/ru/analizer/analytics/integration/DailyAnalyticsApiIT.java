package ru.analizer.analytics.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ru.analizer.support.AbstractHttpIntegrationTest;
import ru.analizer.support.FixtureAdapterConfig;
import ru.analizer.support.FixtureAdapters;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REST-слой ежедневной аналитики: настоящий HTTP-запрос к поднятому приложению.
 *
 * <p>Проверяется не только расчёт, но и форма ответа: будущий frontend должен получить
 * и основные колонки, и детализацию для подробного отчёта.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FixtureAdapterConfig.class)
class DailyAnalyticsApiIT extends AbstractHttpIntegrationTest {


    private static final tools.jackson.databind.ObjectMapper MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private static final String DAY_2026_04_10 = "fixtures/accruals-2026-04-10.json";


    private void syncApr10() {
        FixtureAdapters.FIXTURES.clear();
        FixtureAdapters.FIXTURES.put(LocalDate.of(2026, 4, 10), DAY_2026_04_10);
        accrualImportService.refreshAccrualTypes(accountId());
        accrualImportService.importAccruals(accountId(), LocalDate.of(2026, 4, 10), LocalDate.of(2026, 4, 10));
    }

    @Test
    @DisplayName("GET /api/accounts/{id}/analytics/daily отдаёт доходы, расходы и к выплате за день")
    void dailyReport() throws Exception {
        syncApr10();

        HttpResponse<String> response = get("/api/accounts/" + accountId() + "/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10");
        assertThat(response.statusCode()).as("ответ: %s", response.body()).isEqualTo(200);

        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("marketplace").asString()).isEqualTo("OZON");
        assertThat(body.get("reconciled").asBoolean())
                .as("доходы минус расходы должны равняться данным OZON")
                .isTrue();

        JsonNode row = body.get("days").get(0);
        assertThat(row.get("date").asString()).isEqualTo("2026-04-10");
        assertThat(bd(row, "income")).isEqualByComparingTo("31165.00");
        assertThat(bd(row, "expenses")).isEqualByComparingTo("19867.77");
        assertThat(bd(row, "payout")).isEqualByComparingTo("11297.23");

        // Детализация обязана приходить вместе с итогом: подробный отчёт собирается из неё.
        JsonNode breakdown = row.get("breakdown");
        assertThat(breakdown.properties().stream().map(java.util.Map.Entry::getKey))
                .contains("sales", "returns", "partnerProgramme", "commission", "logistics", "otherExpenses");
        assertThat(new BigDecimal(breakdown.get("sales").asString())).isEqualByComparingTo("19804.67");
        assertThat(new BigDecimal(breakdown.get("returns").asString())).isEqualByComparingTo("-1039.05");
        assertThat(new BigDecimal(breakdown.get("partnerProgramme").asString()))
                .isEqualByComparingTo("12399.38");
        assertThat(new BigDecimal(breakdown.get("commission").asString())).isEqualByComparingTo("-12879.33");
        assertThat(new BigDecimal(breakdown.get("logistics").asString())).isEqualByComparingTo("-3000.71");
        assertThat(new BigDecimal(breakdown.get("otherExpenses").asString())).isEqualByComparingTo("-3987.73");

        JsonNode byType = row.get("expensesByType");
        assertThat(byType.isArray()).isTrue();
        assertThat(byType.size()).isEqualTo(9);
        assertThat(byType.get(0).get("name").asString()).isEqualTo("PayPerClick");
        assertThat(byType.get(0).get("typeId").asInt()).isEqualTo(41);
        assertThat(new BigDecimal(byType.get(0).get("amount").asString()))
                .isEqualByComparingTo("-2267.09");

        // Итог за период отдаётся явно, а не вычисляется клиентом из total.
        assertThat(new BigDecimal(body.get("income").asString())).isEqualByComparingTo("31165.00");
        assertThat(new BigDecimal(body.get("expenses").asString())).isEqualByComparingTo("19867.77");
        assertThat(new BigDecimal(body.get("payout").asString())).isEqualByComparingTo("11297.23");
        assertThat(body.get("total").isObject()).isTrue();

        // Расходы по статьям: их читает карточка расходов, а без расшифровки её
        // сумму нельзя проверить по детализации.
        JsonNode groups = body.get("expenseBreakdown").get("groups");
        assertThat(groups.isArray()).isTrue();
        assertThat(groups.get(0).get("category").asString()).isEqualTo("COMMISSION");
        assertThat(groups.get(0).get("label").asString()).isEqualTo("Комиссия");
        assertThat(new BigDecimal(groups.get(0).get("amount").asString()))
                .isEqualByComparingTo("-12879.33");

        JsonNode promotion = groups.get(1);
        assertThat(promotion.get("category").asString()).isEqualTo("PROMOTION");
        assertThat(new BigDecimal(promotion.get("amount").asString()))
                .isEqualByComparingTo("-3059.40");
        assertThat(promotion.get("items").get(0).get("name").asString()).isEqualTo("PayPerClick");
        assertThat(promotion.get("items").get(0).get("typeId").asInt()).isEqualTo(41);

        assertThat(new BigDecimal(body.get("expenseBreakdown").get("total").asString()))
                .as("раскладка покрывает расходы целиком")
                .isEqualByComparingTo("-19867.77");

        // Строка дня тоже несёт раскладку: по ней строятся строки таблицы и CSV.
        assertThat(row.get("expenseBreakdown").get("groups").size()).isEqualTo(groups.size());
    }

    @Test
    @DisplayName("Отбор по skus сужает отчёт и отдаёт выплату как null, а не как ноль")
    void skuScopeReportsUnknownPayoutAsNull() throws Exception {
        syncApr10();

        HttpResponse<String> response = get("/api/accounts/" + accountId()
                + "/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10&skus=3753845770");
        assertThat(response.statusCode()).as("ответ: %s", response.body()).isEqualTo(200);

        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("filter").asString()).isEqualTo("SKU 3753845770");
        assertThat(body.get("scoped").asBoolean()).isTrue();
        assertThat(new BigDecimal(body.get("income").asString())).isEqualByComparingTo("6420.00");
        assertThat(new BigDecimal(body.get("expenses").asString())).isEqualByComparingTo("3580.59");

        // Выплата по части товаров неизвестна. Ноль в JSON означал бы «выплаты не
        // было», поэтому поле обязано быть null — и в итоге, и в строке дня.
        assertThat(body.get("payout").isNull()).isTrue();
        assertThat(body.get("total").get("payout").isNull()).isTrue();
        assertThat(body.get("days").get(0).get("payout").isNull()).isTrue();
        assertThat(body.get("reconciled").isNull())
                .as("сверять не с чем — «не сходится» было бы таким же вымыслом")
                .isTrue();

        // Расходы без SKU не вошли в отбор, но их сумма приходит явно.
        assertThat(new BigDecimal(body.get("unallocatedExpenses").asString()))
                .isEqualByComparingTo("3566.12");
    }

    @Test
    @DisplayName("Один и тот же sku в параметрах дважды не ломает отчёт")
    void repeatedSkusAreDeduplicated() throws Exception {
        syncApr10();

        JsonNode body = MAPPER.readTree(get("/api/accounts/" + accountId()
                + "/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10"
                + "&skus=3753845770&skus=3753845770").body());

        assertThat(body.get("filter").asString()).isEqualTo("SKU 3753845770");
        assertThat(new BigDecimal(body.get("income").asString()))
                .as("повтор не должен удваивать показатели")
                .isEqualByComparingTo("6420.00");
    }

    @Test
    @DisplayName("Отбор по author сужает отчёт его товарами")
    void authorScopeFiltersReport() throws Exception {
        syncApr10();
        // Авторы лежат в каталоге, поэтому без него отбор по автору не нашёл бы
        // ни одного товара.
        catalogImportService.importProducts(accountId(), null);

        JsonNode body = MAPPER.readTree(get("/api/accounts/" + accountId()
                + "/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10"
                + "&author=" + java.net.URLEncoder.encode("Сурцуков Анатолий",
                        java.nio.charset.StandardCharsets.UTF_8)).body());

        assertThat(body.get("filter").asString()).isEqualTo("Сурцуков Анатолий");
        assertThat(new BigDecimal(body.get("income").asString())).isEqualByComparingTo("2380.00");
        assertThat(new BigDecimal(body.get("expenses").asString())).isEqualByComparingTo("1311.69");
        assertThat(body.get("reconciled").isNull()).isTrue();
    }

    @Test
    @DisplayName("В отчёте без отбора filter описывает весь магазин, а не пустоту")
    void unscopedReportNamesWholeShop() throws Exception {
        syncApr10();

        JsonNode body = MAPPER.readTree(get("/api/accounts/" + accountId()
                + "/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10").body());

        assertThat(body.get("filter").asString()).isEqualTo("Все товары");
        assertThat(body.get("scoped").asBoolean()).isFalse();
        assertThat(new BigDecimal(body.get("unallocatedExpenses").asString()))
                .isEqualByComparingTo("0");
        assertThat(body.get("reconciled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Период без операций возвращает нули, а не ошибку")
    void emptyPeriodReturnsZeros() throws Exception {
        syncApr10();

        JsonNode body = MAPPER.readTree(
                get("/api/accounts/" + accountId() + "/analytics/daily?dateFrom=2026-05-01&dateTo=2026-05-03").body());

        assertThat(new BigDecimal(body.get("payout").asString())).isEqualByComparingTo("0");
        assertThat(body.get("reconciled").asBoolean()).isTrue();
        assertThat(body.get("days").size()).isEqualTo(3);
    }

    @Test
    @DisplayName("Неверный период возвращает 400 с понятным сообщением")
    void invalidPeriodReturnsBadRequest() throws Exception {
        HttpResponse<String> response = get("/api/accounts/" + accountId() + "/analytics/daily?dateFrom=2026-04-12&dateTo=2026-04-10");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("dateTo");
    }

    @Test
    @DisplayName("Отсутствующие параметры возвращают 400, а не 500")
    void missingParametersReturnBadRequest() throws Exception {
        assertThat(get("/api/accounts/" + accountId() + "/analytics/daily").statusCode()).isEqualTo(400);
        assertThat(get("/api/accounts/" + accountId() + "/analytics/daily?dateFrom=2026-04-10").statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Ответ — валидный JSON со всеми ожидаемыми полями")
    void responseShapeIsStable() throws Exception {
        syncApr10();

        JsonNode body = MAPPER.readTree(
                get("/api/accounts/" + accountId() + "/analytics/daily?dateFrom=2026-04-09&dateTo=2026-04-11").body());

        // Пустые дни в периоде тоже присутствуют: пропуск дня молча выглядел бы
        // как «денег не было», хотя данных могло просто не быть в базе.
        List<String> dates = body.get("days").findValuesAsString("date");
        assertThat(dates).containsExactly("2026-04-09", "2026-04-10", "2026-04-11");
    }

    @Test
    @DisplayName("Отчёт по неизвестному аккаунту говорит «данных нет», а не отдаёт нули")
    void reportForUnknownAccountSaysNotLoaded() throws Exception {
        // Раньше здесь был 500 с пустым телом. Теперь отчёт прямо говорит, что данных
        // нет, и перечисляет недостающие дни — этого достаточно, чтобы предложить загрузку.
        HttpResponse<String> response = get(
                "/api/accounts/" + accountId() + "/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-12");

        assertThat(response.statusCode()).isEqualTo(200);

        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("status").asString()).isEqualTo("NOT_LOADED");

        JsonNode coverage = body.get("coverage");
        assertThat(coverage.get("requestedDays").asInt()).isEqualTo(3);
        assertThat(coverage.get("loadedDays").asInt()).isZero();
        assertThat(coverage.get("needsSync").asBoolean()).isTrue();
        assertThat(coverage.get("missingDays").size()).isEqualTo(3);

        // И сам отчёт не притворяется, что это «денег не было».
        assertThat(new BigDecimal(body.get("payout").asString())).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Покрытие отвечает даже до первой синхронизации")
    void coverageAnswersBeforeFirstSync() throws Exception {
        HttpResponse<String> response = get(
                "/api/accounts/" + accountId() + "/data/coverage?dateFrom=2026-04-10&dateTo=2026-04-12");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = MAPPER.readTree(response.body());
        assertThat(body.get("requestedDays").asInt()).isEqualTo(3);
        assertThat(body.get("loadedDays").asInt()).isZero();
        assertThat(body.get("needsSync").asBoolean()).isTrue();
        assertThat(body.get("missingDays").size()).isEqualTo(3);
    }

    private static BigDecimal bd(JsonNode node, String field) {
        return new BigDecimal(node.get(field).asString());
    }
}