package ru.analizer.account.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.support.AbstractHttpIntegrationTest;
import ru.analizer.support.FixtureAdapterConfig;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Подключение маркетплейса реквизитами.
 *
 * <p>Адаптер маркетплейса подменён заглушкой, которая проверку ключей всегда проходит:
 * здесь интересует не OZON, а то, что происходит с аккаунтом — создаётся он, обновляется
 * и достаётся ли наружу то, что нужно контроллеру.
 */
@org.springframework.boot.test.context.SpringBootTest(
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(FixtureAdapterConfig.class)
class MarketplaceCredentialsIT extends AbstractHttpIntegrationTest {

    @Test
    @DisplayName("Подключение создаёт аккаунт и возвращает код маркетплейса")
    void connectCreatesAccount() throws Exception {
        HttpResponse<String> response = putCredentials("1154", "key-1");

        assertThat(response.statusCode()).as("ответ: %s", response.body()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"marketplace\":\"OZON\"")
                .contains("\"clientId\":\"1154\"")
                .doesNotContain("key-1");

        assertThat(jdbc.queryForObject(
                "select api_key from seller_account where client_id = '1154'", String.class))
                .as("ключ должен лежать у аккаунта")
                .isEqualTo("key-1");
    }

    @Test
    @DisplayName("Повторное подключение обновляет ключи, а не падает")
    void connectTwiceUpdatesCredentials() throws Exception {
        // Именно этот сценарий раньше давал 500: контроллер читал код маркетплейса
        // на ленивом прокси уже после закрытия транзакции. Первый вызов проходил,
        // потому что аккаунт только что создавался, а второй падал.
        assertThat(putCredentials("1154", "key-1").statusCode()).isEqualTo(200);

        HttpResponse<String> second = putCredentials("1154", "key-2");

        assertThat(second.statusCode()).as("ответ: %s", second.body()).isEqualTo(200);
        assertThat(second.body())
                .contains("\"marketplace\":\"OZON\"")
                .doesNotContain("key-2");
        assertThat(jdbc.queryForObject(
                "select api_key from seller_account where client_id = '1154'", String.class))
                .as("ключ должен замениться, а не добавиться")
                .isEqualTo("key-2");

        // Ключи у маркетплейсов истекают, и менять их приходится, поэтому повторное
        // подключение обязано работать, а не быть разовым действием.
        assertThat(jdbc.queryForObject(
                "select count(*) from seller_account where client_id = '1154'", Long.class))
                .as("аккаунт должен остаться один: один магазин на маркетплейс у пользователя")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Неизвестный маркетплейс — 404, а не 500")
    void unknownMarketplaceIsNotFound() throws Exception {
        HttpResponse<String> response = putCredentialsTo("wildberries", "1154", "key");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("unknown-marketplace");
    }

    @Test
    @DisplayName("Отвергнутые ключи не сохраняются")
    void rejectedCredentialsAreNotStored() throws Exception {
        // Заглушка адаптера настроена так, что «неверный» ключ она отвергает.
        HttpResponse<String> response = putCredentials("1154", "reject-me");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("credentials-rejected");
        assertThat(count("seller_account"))
                .as("неверный ключ не должен оставлять после себя аккаунт")
                .isZero();
    }

    @Test
    @DisplayName("Без подключения импорт не запускается — 409, а не пустой отчёт")
    void importRequiresConnection() throws Exception {
        HttpResponse<String> response = post("/api/marketplaces/ozon/imports/catalog");

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("not-connected");
    }

    @Test
    @DisplayName("Удаление магазина уносит вместе с ним все его данные")
    void deleteRemovesAccountAndItsData() throws Exception {
        assertThat(putCredentials("1154", "key-1").statusCode()).isEqualTo(200);
        Long accountId = jdbc.queryForObject(
                "select id from seller_account where client_id = '1154'", Long.class);
        seedData(accountId);
        // Справочник типов общий для маркетплейса: его удаление магазина не должно
        // затронуть, иначе у следующего продавца расходы остались бы без названий.
        seedAccrualType();

        HttpResponse<String> response = delete("/api/marketplaces/ozon");

        assertThat(response.statusCode()).as("ответ: %s", response.body()).isEqualTo(204);
        assertThat(response.body()).as("ответ должен быть пустым").isEmpty();

        assertThat(count("seller_account")).as("аккаунт удалён").isZero();
        assertThat(count("finance_accrual")).as("начисления ушли каскадом").isZero();
        assertThat(count("imported_day")).as("дни загрузки ушли каскадом").isZero();
        assertThat(count("ozon_product")).as("каталог ушёл каскадом").isZero();
        assertThat(count("product_author")).as("авторы ушли каскадом").isZero();

        assertThat(count("accrual_type"))
                .as("справочник типов общий для маркетплейса и должен был уцелеть")
                .isEqualTo(1L);
        assertThat(count("marketplace")).as("маркетплейс остался в справочнике").isPositive();
    }

    @Test
    @DisplayName("После удаления магазин можно подключить заново")
    void storeCanBeConnectedAgainAfterDelete() throws Exception {
        assertThat(putCredentials("1154", "key-1").statusCode()).isEqualTo(200);
        assertThat(delete("/api/marketplaces/ozon").statusCode()).isEqualTo(204);

        HttpResponse<String> again = putCredentials("1154", "key-2");

        assertThat(again.statusCode()).isEqualTo(200);
        assertThat(jdbc.queryForObject(
                "select api_key from seller_account where client_id = '1154'", String.class))
                .isEqualTo("key-2");
    }

    @Test
    @DisplayName("После удаления отчёт честно говорит NOT_LOADED, а не показывает нули")
    void reportAfterDeleteSaysNotLoaded() throws Exception {
        assertThat(putCredentials("1154", "key-1").statusCode()).isEqualTo(200);
        seedData(jdbc.queryForObject(
                "select id from seller_account where client_id = '1154'", Long.class));

        assertThat(delete("/api/marketplaces/ozon").statusCode()).isEqualTo(204);

        HttpResponse<String> report = get(
                "/api/marketplaces/ozon/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10");

        assertThat(report.statusCode()).isEqualTo(200);
        assertThat(report.body())
                // Главное: приложение обязано сказать «данных нет», а не отдать нули,
                // которые читаются как «денег не было».
                .contains("\"status\":\"NOT_LOADED\"");
    }

    @Test
    @DisplayName("Удаление неподключённого маркетплейса — 409, а не 204")
    void deleteWithoutConnectionIsConflict() throws Exception {
        HttpResponse<String> response = delete("/api/marketplaces/ozon");

        assertThat(response.statusCode())
                .as("нечего удалять — клиент должен это понимать, а не считать успехом")
                .isEqualTo(409);
        assertThat(response.body()).contains("not-connected");
    }

    @Test
    @DisplayName("Удаление без сессии — 401")
    void deleteWithoutSessionIsUnauthorized() throws Exception {
        assertThat(putCredentials("1154", "key-1").statusCode()).isEqualTo(200);

        HttpResponse<String> response = deleteAnonymous("/api/marketplaces/ozon");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(count("seller_account"))
                .as("чужой вызов не должен ничего удалять")
                .isEqualTo(1L);
    }

    /** Заполняет таблицы аккаунта, чтобы проверить каскад. */
    private void seedData(Long accountId) {
        jdbc.update("""
                insert into imported_day (seller_account_id, day, status, is_final,
                                          accrual_count, total_amount, change_count)
                values (?, '2026-04-10', 'DONE', true, 1, 100.00, 0)
                """, accountId);
        jdbc.update("""
                insert into finance_accrual (seller_account_id, external_id, accrual_date,
                                            unit_number, accrued_category, ozon_type_id,
                                            total_amount, currency, raw_data)
                values (?, 1, '2026-04-10', 'u-1', 'NON_ITEM', 1, 100.00, 'RUB', '{}'::jsonb)
                """, accountId);
        jdbc.update("""
                insert into ozon_product (seller_account_id, sku, name, raw_data)
                values (?, 100, 'Тестовый товар', '{}'::jsonb)
                """, accountId);
    }



        /** Тип начисления в справочнике маркетплейса — он живёт дольше любого магазина. */
    private void seedAccrualType() {
        jdbc.update("""
                insert into accrual_type (marketplace_id, external_type_id, name)
                values ((select id from marketplace where code = 'OZON'), 1, 'Тестовый тип')
                """);
    }

    private HttpResponse<String> putCredentials(String clientId, String apiKey) throws Exception {
        return putCredentialsTo("ozon", clientId, apiKey);
    }

    private HttpResponse<String> putCredentialsTo(String marketplace, String clientId, String apiKey)
            throws Exception {
        return putJson("/api/marketplaces/" + marketplace + "/credentials",
                "{\"clientId\":\"" + clientId + "\",\"apiKey\":\"" + apiKey + "\"}");
    }
}