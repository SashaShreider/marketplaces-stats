package ru.analizer.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import ru.analizer.support.AbstractHttpIntegrationTest;
import ru.analizer.support.FixtureAdapterConfig;

import java.net.URI;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт HTTP: пути, коды ответов и формат ошибок.
 *
 * <p>Проверяется именно HTTP, а не вызовы сервисов: переименование путей ломает клиента
 * даже тогда, когда все тесты сервисов зелёные.
 *
 * <p>Пустые ответы не вызывают обращений к OZON: адаптер подменён, а методы чтения
 * наружу не ходят по замыслу — если метод начнёт ходить, тесты это покажут.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(FixtureAdapterConfig.class)
class ApiContractIT extends AbstractHttpIntegrationTest {




    /**
     * Секретный ключ маркетплейса не должен попадать ни в один ответ.
     *
     * <p>Проверяется на живом HTTP, а не по коду: утечка обычно случается не в том
     * методе, который её добавил, а в том, который возвращает сущность целиком.
     */
    @Test
    @DisplayName("API-ключ не утекает в ответе списка магазинов")
    void apiKeyNeverLeaksToClient() throws Exception {
        account();

        HttpResponse<String> response = get("/api/accounts");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .as("ответ должен показывать свой магазин")
                .contains(CLIENT_ID)
                .doesNotContain(API_KEY);
    }

    @Test
    @DisplayName("Список магазинов отвечает 200 и пустым массивом до подключения")
    void accountsAreEmptyBeforeConnecting() throws Exception {
        HttpResponse<String> response = get("/api/accounts");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .as("пустой список, а не null: клиенту не приходится различать")
                .startsWith("[");

        // Справочник платформ при этом остаётся: он отвечает на вопрос «куда подключаться»,
        // и без него пользователь не увидит, что Wildberries поддерживается.
        assertThat(get("/api/marketplaces").body()).contains("OZON");
    }

    @Test
    @DisplayName("Два магазина одной платформы возвращаются оба")
    void severalAccountsOnOnePlatform() throws Exception {
        account();
        secondAccount("7777");

        HttpResponse<String> response = get("/api/accounts");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .as("оба кабинета одного продавца должны быть видны")
                .contains(CLIENT_ID)
                .contains("7777");
        // Платформа в списке магазинов остаётся одна: это справочник платформ, а не
        // список магазинов, иначе одна площадка занимала бы столько строк, сколько
        // у продавца кабинетов.
        assertThat(get("/api/marketplaces").body())
                .doesNotContain("7777");
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    @Test
    @DisplayName("Список маркетплейсов отвечает 200 даже на пустой базе")
    void marketplacesAreListed() throws Exception {
        HttpResponse<String> response = get("/api/marketplaces");

        assertThat(response.statusCode()).as("ответ: %s", response.body()).isEqualTo(200);
        assertThat(response.body()).contains("OZON");
    }

    @Test
    @DisplayName("Несуществующий магазин — 404, а не пустой отчёт")
    void unknownAccountIsNotFound() throws Exception {
        HttpResponse<String> response = get(
                "/api/accounts/999999/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("\"type\":\"urn:analizer:error:account-not-found\"")
                .contains("999999");
    }

    @Test
    @DisplayName("Чужой магазин неотличим от несуществующего")
    void foreignAccountLooksAbsent() throws Exception {
        // Различая «нет такого» и «не твой», мы пересказывали бы злоумышленнику,
        // какие магазины существуют. Поэтому код и тип ошибки совпадают, а отличаются
        // только идентификаторы из самих запросов.
        account();
        Long foreignId = otherUsersAccountId();

        HttpResponse<String> absent = get(
                "/api/accounts/999999/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10");
        HttpResponse<String> foreign = get(
                "/api/accounts/" + foreignId + "/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10");

        assertThat(foreign.statusCode()).isEqualTo(404);
        assertThat(foreign.body())
                .as("ответ на чужой магазин совпадает по типу ошибки, а отличается лишь числом из запроса")
                .contains("\"type\":\"urn:analizer:error:account-not-found\"")
                .doesNotContain(CLIENT_ID)
                .doesNotContain("секретный")
                .doesNotContain("9999");
        assertThat(foreign.body().replace(String.valueOf(foreignId), "X"))
                .isEqualTo(absent.body().replace("999999", "X"));
    }

    /**
     * Магазин чужого пользователя.
     *
     * <p>Создаётся напрямую в базе: HTTP-запрос на его создание от имени текущего
     * пользователя по определению невозможен, а проверять надо именно невозможность
     * добраться до чужого.
     */
    private Long otherUsersAccountId() {
        var otherUser = appUserRepository.save(new ru.analizer.auth.domain.AppUser(
                "other-seller-" + java.util.UUID.randomUUID().toString().substring(0, 8),
                passwordEncoder.encode(PASSWORD), "Другой продавец"));
        return sellerAccountRepository.save(new ru.analizer.account.domain.SellerAccount(
                otherUser, marketplace(), "Чужой магазин", "9999", "secret")).getId();
    }

    @Test
    @DisplayName("Ошибка в формате RFC 7807 с машиночитаемым типом")
    void errorsFollowProblemDetails() throws Exception {
        HttpResponse<String> response = get("/api/accounts/999999/imports");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .contains("\"type\":\"urn:analizer:error:account-not-found\"");
    }

    @Test
    @DisplayName("Покрытие до первой синхронизации — 200 с честными данными, а не ошибка")
    void coverageBeforeAnyImport() throws Exception {
        HttpResponse<String> response = get(
                "/api/accounts/" + accountId() + "/data/coverage?dateFrom=2026-09-01&dateTo=2026-09-30");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"requestedDays\":30")
                .contains("\"loadedDays\":0")
                .contains("\"complete\":false");
    }

    @Test
    @DisplayName("Обратный период — 400 с понятным текстом")
    void reversedPeriodIsBadRequest() throws Exception {
        HttpResponse<String> response = get(
                "/api/accounts/" + accountId() + "/analytics/daily?dateFrom=2026-09-30&dateTo=2026-09-01");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("dateTo не может быть раньше dateFrom");
    }

    @Test
    @DisplayName("Список прогонов всегда массив, даже когда их ещё не было")
    void importListIsAlwaysArray() throws Exception {
        account();

        HttpResponse<String> response = get("/api/accounts/" + accountId() + "/imports");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).startsWith("[");
    }

    @Test
    @DisplayName("Несуществующий прогон — 404, а не 200 с пустым объектом")
    void unknownImportIsNotFound() throws Exception {
        account();

        HttpResponse<String> response = get("/api/accounts/" + accountId() + "/imports/999999");

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Отчёт по товарам на пустой базе — NOT_LOADED, а не нули без статуса")
    void productReportBeforeImport() throws Exception {
        HttpResponse<String> response = get(
                "/api/accounts/" + accountId() + "/analytics/products?dateFrom=2026-09-01&dateTo=2026-09-30");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"status\":\"NOT_LOADED\"")
                .contains("\"needsSync\":true");
    }

    @Test
    @DisplayName("В ответе больше нет параметра clientId")
    void clientIdIsGone() throws Exception {
        // Старый контракт принимал ?clientId= и молча игнорировал его. Теперь его нет,
        // и переданный параметр не должен влиять на результат.
        account();
        HttpResponse<String> withLegacyParam = get(
                "/api/accounts/" + accountId() + "/data/coverage?dateFrom=2026-04-10&dateTo=2026-04-10&clientId=9999");
        HttpResponse<String> withoutParam = get(
                "/api/accounts/" + accountId() + "/data/coverage?dateFrom=2026-04-10&dateTo=2026-04-10");

        assertThat(withLegacyParam.statusCode()).isEqualTo(200);
        assertThat(withLegacyParam.body()).isEqualTo(withoutParam.body());
    }

    @Test
    @DisplayName("Запуск импорта возвращает 202 и номер прогона")
    void importStartsAsynchronously() throws Exception {
        account();

        HttpResponse<String> response = post("/api/accounts/" + accountId() + "/imports/catalog");

        assertThat(response.statusCode()).as("ответ: %s", response.body()).isEqualTo(202);
        assertThat(response.body())
                .contains("\"importType\":\"CATALOG\"")
                .contains("\"dateFrom\":null");
        awaitJob(mapper.readTree(response.body()).get("id").asLong());
    }

    private static final tools.jackson.databind.ObjectMapper mapper =
            tools.jackson.databind.json.JsonMapper.builder()
                    .disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
}