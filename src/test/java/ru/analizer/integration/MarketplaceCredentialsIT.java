package ru.analizer.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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

    private HttpResponse<String> putCredentials(String clientId, String apiKey) throws Exception {
        return putCredentialsTo("ozon", clientId, apiKey);
    }

    private HttpResponse<String> putCredentialsTo(String marketplace, String clientId, String apiKey)
            throws Exception {
        return putJson("/api/marketplaces/" + marketplace + "/credentials",
                "{\"clientId\":\"" + clientId + "\",\"apiKey\":\"" + apiKey + "\"}");
    }
}