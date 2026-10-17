package ru.analizer.account.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.support.AbstractHttpIntegrationTest;
import ru.analizer.support.FixtureAdapterConfig;
import ru.analizer.support.StubOzonClient;

import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Подключение магазина реквизитами, смена ключей и отключение.
 *
 * <p>Адаптер маркетплейса подменён заглушкой, которая проверку ключей всегда проходит:
 * здесь интересует не OZON, а то, что происходит с магазином — создаётся ли он,
 * обновляется и достаётся ли наружу то, что нужно контроллеру.
 */
@org.springframework.boot.test.context.SpringBootTest(
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.context.annotation.Import(FixtureAdapterConfig.class)
class AccountConnectionIT extends AbstractHttpIntegrationTest {

    @Test
    @DisplayName("Название магазина берётся у маркетплейса, а не собирается из Client-Id")
    void shopNameComesFromMarketplace() throws Exception {
        connect("1154", "key-1");

        String name = jdbc.queryForObject("select name from seller_account", String.class);
        assertThat(name)
                .as("название из ответа маркетплейса, а не «OZON 1154»")
                .isEqualTo(StubOzonClient.SHOP_NAME)
                .isNotEqualTo("OZON 1154");
    }

    @Test
    @DisplayName("Подключение создаёт магазин и возвращает его идентификатор")
    void connectCreatesAccount() throws Exception {
        HttpResponse<String> response = connect("1154", "key-1");

        assertThat(response.statusCode()).as("ответ: %s", response.body()).isEqualTo(201);
        assertThat(response.body())
                .contains("\"marketplaceCode\":\"OZON\"")
                .contains("\"clientId\":\"1154\"")
                .doesNotContain("key-1");

        assertThat(jdbc.queryForObject(
                "select api_key from seller_account where client_id = '1154'", String.class))
                .as("ключ должен лежать у магазина")
                .isEqualTo("key-1");
    }

    /**
     * Два кабинета одной платформы — обычное дело у продавца, и раньше это было
     * невозможно: ограничение в базе разрешало один магазин на маркетплейс.
     */
    @Test
    @DisplayName("Второй магазин той же платформы заводится отдельным")
    void secondShopOnSamePlatformIsAllowed() throws Exception {
        assertThat(connect("1154", "key-1").statusCode()).isEqualTo(201);

        HttpResponse<String> second = connect("7777", "key-2");

        assertThat(second.statusCode()).as("ответ: %s", second.body()).isEqualTo(201);
        assertThat(count("seller_account")).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "select api_key from seller_account where client_id = '1154'", String.class))
                .as("ключи первого магазина не должны перезаписаться вторым")
                .isEqualTo("key-1");
        assertThat(jdbc.queryForObject(
                "select api_key from seller_account where client_id = '7777'", String.class))
                .isEqualTo("key-2");
    }

    @Test
    @DisplayName("Повторное подключение того же кабинета — 409 с понятным текстом")
    void connectingSameShopTwiceIsConflict() throws Exception {
        assertThat(connect("1154", "key-1").statusCode()).isEqualTo(201);

        HttpResponse<String> second = connect("1154", "key-2");

        // Не 500 из-за нарушения ограничения: уникальность гарантирует база, но внятный
        // ответ она не даёт — его даёт проверка до попытки создать.
        assertThat(second.statusCode()).as("ответ: %s", second.body()).isEqualTo(409);
        assertThat(second.body()).contains("shop-already-connected");
        assertThat(count("seller_account")).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select api_key from seller_account where client_id = '1154'", String.class))
                .as("ключи не должны смениться, пока пользователь их не просил")
                .isEqualTo("key-1");
    }

    /**
     * Смена ключей — отдельное действие от подключения.
     *
     * <p>Ключи у маркетплейсов истекают, и менять их приходится постоянно. Если бы
     * замена шла через подключение, то с двумя магазинами одной платформы она была бы
     * неоднозначной: непонятно было бы, какой из них чинить.
     */
    @Test
    @DisplayName("Замена ключей не заводит новый магазин")
    void replacingKeysUpdatesInPlace() throws Exception {
        Long accountId = connectAndGetId("1154", "key-1");

        HttpResponse<String> second = putJson("/api/accounts/" + accountId + "/credentials",
                "{\"clientId\":\"1154\",\"apiKey\":\"key-2\"}");

        assertThat(second.statusCode()).as("ответ: %s", second.body()).isEqualTo(200);
        assertThat(second.body())
                .contains("\"clientId\":\"1154\"")
                .doesNotContain("key-2");
        assertThat(jdbc.queryForObject(
                "select api_key from seller_account where client_id = '1154'", String.class))
                .as("ключ должен замениться, а не добавиться")
                .isEqualTo("key-2");
        assertThat(count("seller_account"))
                .as("магазин остался один: смена ключей его не плодит")
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Неизвестный маркетплейс — 404, а не 500")
    void unknownMarketplaceIsNotFound() throws Exception {
        HttpResponse<String> response = postJson("/api/accounts",
                "{\"marketplace\":\"wildberries\",\"clientId\":\"1154\",\"apiKey\":\"key\"}");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("unknown-marketplace");
        assertThat(count("seller_account")).isZero();
    }

    @Test
    @DisplayName("Отвергнутые ключи не сохраняются")
    void rejectedCredentialsAreNotStored() throws Exception {
        // Заглушка адаптера настроена так, что «неверный» ключ она отвергает.
        HttpResponse<String> response = connect("1154", "reject-me");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("credentials-rejected");
        assertThat(count("seller_account"))
                .as("неверный ключ не должен оставлять после себя магазин")
                .isZero();
    }

    @Test
    @DisplayName("Импорт несуществующего магазина — 404, а не пустой отчёт")
    void importRequiresExistingAccount() throws Exception {
        HttpResponse<String> response = post("/api/accounts/999999/imports/catalog");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("account-not-found");
    }

    @Test
    @DisplayName("Удаление магазина уносит вместе с ним все его данные")
    void deleteRemovesAccountAndItsData() throws Exception {
        Long accountId = connectAndGetId("1154", "key-1");
        seedData(accountId);
        // Справочник типов общий для платформы: его удаление магазина не должно затронуть,
        // иначе у следующего магазина расходы остались бы без названий.
        seedAccrualType();

        HttpResponse<String> response = delete("/api/accounts/" + accountId);

        assertThat(response.statusCode()).as("ответ: %s", response.body()).isEqualTo(204);
        assertThat(response.body()).as("ответ должен быть пустым").isEmpty();

        assertThat(count("seller_account")).as("магазин удалён").isZero();
        assertThat(count("finance_accrual")).as("начисления ушли каскадом").isZero();
        assertThat(count("imported_day")).as("дни загрузки ушли каскадом").isZero();
        assertThat(count("ozon_product")).as("каталог ушёл каскадом").isZero();
        assertThat(count("product_author")).as("авторы ушли каскадом").isZero();

        assertThat(count("accrual_type"))
                .as("справочник типов общий для платформы и должен был уцелеть")
                .isEqualTo(1L);
        assertThat(count("marketplace")).as("платформа осталась в справочнике").isPositive();
    }

    /**
     * Удаление одного магазина не трогает остальные.
     *
     * <p>Это ровно то, что ломается легче всего: каскад срабатывает по строке аккаунта,
     * и если бы удаление шло по платформе, у продавца с двумя кабинетами исчезли бы
     * данные обоих разом.
     */
    @Test
    @DisplayName("Удаление одного магазина не затрагивает второй")
    void deleteKeepsOtherShops() throws Exception {
        Long first = connectAndGetId("1154", "key-1");
        Long second = connectAndGetId("7777", "key-2");
        seedData(first);
        seedData(second);

        assertThat(delete("/api/accounts/" + first).statusCode()).isEqualTo(204);

        assertThat(count("seller_account")).as("остался один магазин").isEqualTo(1L);
        assertThat(jdbc.queryForObject("select id from seller_account", Long.class))
                .as("остался именно второй, а не первый")
                .isEqualTo(second);
        assertThat(count("finance_accrual"))
                .as("данные первого удалены каскадом, второго — нет")
                .isEqualTo(1L);
        assertThat(jdbc.queryForObject("select seller_account_id from finance_accrual", Long.class))
                .isEqualTo(second);
    }

    @Test
    @DisplayName("После удаления магазин можно подключить заново")
    void storeCanBeConnectedAgainAfterDelete() throws Exception {
        Long accountId = connectAndGetId("1154", "key-1");
        assertThat(delete("/api/accounts/" + accountId).statusCode()).isEqualTo(204);

        HttpResponse<String> again = connect("1154", "key-2");

        assertThat(again.statusCode()).isEqualTo(201);
        assertThat(jdbc.queryForObject(
                "select api_key from seller_account where client_id = '1154'", String.class))
                .isEqualTo("key-2");
    }

    @Test
    @DisplayName("После удаления отчёт честно говорит NOT_LOADED, а не показывает нули")
    void reportAfterDeleteSaysNotLoaded() throws Exception {
        Long accountId = connectAndGetId("1154", "key-1");
        seedData(accountId);
        assertThat(delete("/api/accounts/" + accountId).statusCode()).isEqualTo(204);

        // Тот же отчёт загружается заново, уже на новый магазин: пока данных нет,
        // он обязан сказать об этом прямо, а не отдать нули, которые читаются
        // как «денег не было».
        Long fresh = connectAndGetId("1154", "key-2");
        HttpResponse<String> report = get(
                "/api/accounts/" + fresh + "/analytics/daily?dateFrom=2026-04-10&dateTo=2026-04-10");

        assertThat(report.statusCode()).isEqualTo(200);
        assertThat(report.body()).contains("\"status\":\"NOT_LOADED\"");
    }

    @Test
    @DisplayName("Удаление несуществующего магазина — 404, а не 204")
    void deleteMissingAccountIsNotFound() throws Exception {
        HttpResponse<String> response = delete("/api/accounts/999999");

        assertThat(response.statusCode())
                .as("нечего удалять — клиент должен это понимать, а не считать успехом")
                .isEqualTo(404);
        assertThat(response.body()).contains("account-not-found");
    }

    @Test
    @DisplayName("Удаление без сессии — 401")
    void deleteWithoutSessionIsUnauthorized() throws Exception {
        Long accountId = connectAndGetId("1154", "key-1");

        HttpResponse<String> response = deleteAnonymous("/api/accounts/" + accountId);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(count("seller_account"))
                .as("чужой вызов не должен ничего удалять")
                .isEqualTo(1L);
    }

    /** Заполняет таблицы аккаунта, чтобы проверить каскад. */
    private void seedData(Long accountId) {
        Long externalId = accountId * 100 + 1;
        jdbc.update("""
                insert into imported_day (seller_account_id, day, status, is_final,
                                          accrual_count, total_amount, change_count)
                values (?, '2026-04-10', 'DONE', true, 1, 100.00, 0)
                """, accountId);
        jdbc.update("""
                insert into finance_accrual (seller_account_id, external_id, accrual_date,
                                            unit_number, accrued_category, ozon_type_id,
                                            total_amount, currency, raw_data)
                values (?, ?, '2026-04-10', ?, 'NON_ITEM', 1, 100.00, 'RUB', '{}'::jsonb)
                """, accountId, externalId, "u-" + accountId);
        jdbc.update("""
                insert into ozon_product (seller_account_id, sku, name, raw_data)
                values (?, ?, 'Тестовый товар', '{}'::jsonb)
                """, accountId, 100 + accountId);
    }

    /** Тип начисления в справочнике платформы — он живёт дольше любого магазина. */
    private void seedAccrualType() {
        jdbc.update("""
                insert into accrual_type (marketplace_id, external_type_id, name)
                values ((select id from marketplace where code = 'OZON'), 1, 'Тестовый тип')
                """);
    }

    private HttpResponse<String> connect(String clientId, String apiKey) throws Exception {
        return postJson("/api/accounts",
                "{\"marketplace\":\"ozon\",\"clientId\":\"" + clientId
                        + "\",\"apiKey\":\"" + apiKey + "\"}");
    }

    /** Подключает магазин и достаёт его идентификатор из базы — так же, как это делает контроллер. */
    private Long connectAndGetId(String clientId, String apiKey) throws Exception {
        assertThat(connect(clientId, apiKey).statusCode()).isEqualTo(201);
        return jdbc.queryForObject(
                "select id from seller_account where client_id = ?", Long.class, clientId);
    }
}