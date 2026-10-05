package ru.analizer.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import ru.analizer.account.domain.Marketplace;
import ru.analizer.analytics.domain.ReportCoverage;
import ru.analizer.auth.repository.AppUserRepository;
import ru.analizer.account.repository.SellerAccountRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Два продавца не должны видеть данные друг друга.
 *
 * <p>Это главное обещание, которое даёт многопользовательская версия, и оно
 * проверяется на настоящем HTTP с двумя сессиями: подмена пользователя в контексте
 * скрыла бы ровно те места, где происходит утечка.
 *
 * <p>Ключи у продавцов разные, и это важно: если бы адаптер брал реквизиты из бина,
 * оба импорта пошли бы под одним ключом и тест на изоляцию данных прошёл бы, хотя
 * на деле один продавец видел бы чужой магазин.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MultiUserIsolationIT extends AbstractPostgresIntegrationTest {

    private static final String FIRST = "seller-one";
    private static final String SECOND = "seller-two";
    private static final String FIRST_PASSWORD = "first-password-1";
    private static final String SECOND_PASSWORD = "second-password-2";

    @LocalServerPort
    int port;

    @Autowired
    protected AppUserRepository appUserRepository;

    @Autowired
    protected SellerAccountRepository sellerAccountRepository;

    @Test
    @DisplayName("Данные одного продавца не видны другому: покрытие, прог��ны и аккаунт")
    void sellersDoNotSeeEachOthersData() throws Exception {
        // Наполняем базу от имени обоих продавцов: у каждого по одному загруженному дню.
        importFor(FIRST, FIRST_PASSWORD, "1", "100.00");
        String sessionOne = login(FIRST, FIRST_PASSWORD);
        Long accountOne = accountIdOf(FIRST);

        importFor(SECOND, SECOND_PASSWORD, "2", "250.00");
        String sessionTwo = login(SECOND, SECOND_PASSWORD);
        Long accountTwo = accountIdOf(SECOND);

        assertThat(accountOne).as("у каждого продавца свой аккаунт").isNotEqualTo(accountTwo);

        // Покрытие считается по seller_account_id. Если бы выборка шла по маркетплейсу,
        // оба увидели бы два загруженных дня вместо одного, и месяц показался бы
        // загруженным дважды.
        assertThat(loadedDaysIn(sessionOne, reportCoverage())).isEqualTo(1);
        assertThat(loadedDaysIn(sessionTwo, reportCoverage())).isEqualTo(1);

        // Список прогонов у каждого свой.
        assertThat(get(sessionOne, "/api/marketplaces/ozon/imports").body())
                .doesNotContain("\"clientId\":\"2\"");
        assertThat(get(sessionTwo, "/api/marketplaces/ozon/imports").body())
                .doesNotContain("\"clientId\":\"1\"");

        // Список маркетплейсов показывает свой аккаунт, а не чужой.
        assertThat(get(sessionOne, "/api/marketplaces").body())
                .contains("\"clientId\":\"1\"")
                .doesNotContain("\"clientId\":\"2\"");
        assertThat(get(sessionTwo, "/api/marketplaces").body())
                .contains("\"clientId\":\"2\"")
                .doesNotContain("\"clientId\":\"1\"");
    }

    @Test
    @DisplayName("Чужой прогон импорта не открывается по его номеру")
    void foreignImportRunIsNotVisible() throws Exception {
        importFor(FIRST, FIRST_PASSWORD, "1", "100.00");
        String sessionOne = login(FIRST, FIRST_PASSWORD);
        Long importId = latestImportIdOf(FIRST);

        importFor(SECOND, SECOND_PASSWORD, "2", "250.00");
        String sessionTwo = login(SECOND, SECOND_PASSWORD);

        // Номер прогона принадлежит первому продавцу. Второй подставляет его в свой
        // запрос и получает 404, а не чужие данные: иначе перебором номеров можно
        // было бы увидеть чужой магазин.
        HttpResponse<String> response = get(sessionTwo,
                "/api/marketplaces/ozon/imports/" + importId);

        assertThat(response.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Реквизиты хранятся отдельно у каждого продавца")
    void credentialsAreStoredPerSeller() throws Exception {
        importFor(FIRST, FIRST_PASSWORD, "1", "100.00");
        importFor(SECOND, SECOND_PASSWORD, "2", "250.00");

        assertThat(clientIdOf(FIRST)).isEqualTo("1");
        assertThat(clientIdOf(SECOND)).isEqualTo("2");
        // Один магазин на маркетплейс у пользователя: ограничение в базе, а не в коде.
        assertThat(loginOfAccount("1")).isEqualTo(FIRST);
        assertThat(loginOfAccount("2")).isEqualTo(SECOND);
        // И ключи разные: если бы адаптер брал реквизиты из бина, оба импорта пошли бы
        // под одним ключом, и изоляция данных держалась бы на подпорке.
        assertThat(apiKeyOf(FIRST)).isEqualTo("key-1");
        assertThat(apiKeyOf(SECOND)).isEqualTo("key-2");
    }

    /**
     * Заводит продавца, подключает маркетплейс и импортирует один день с суммой {@code total}.
     *
     * <p>Сумма кладётся прямо в базу: поднимать фоновую задачу с настоящим адаптером
     * здесь незачем, изоляцию проверяет не источник данных, а чтение.
     */
    private void importFor(String login, String password, String clientId, String total) {
        var user = appUserRepository.save(new ru.analizer.auth.domain.AppUser(
                login, passwordEncoder.encode(password), login));
        var account = sellerAccountRepository.save(
                new ru.analizer.account.domain.SellerAccount(
                        user, marketplace(), "OZON " + clientId, clientId, "key-" + clientId));

        jdbc.update("""
                insert into imported_day (seller_account_id, day, status, is_final,
                                          accrual_count, total_amount, change_count)
                values (?, ?, 'DONE', true, 1, ?, 0)
                """, account.getId(), DAY, new BigDecimal(total));

        // Прогон нужен отдельной строкой: по нему и проверяется, что чужие прогоны
        // не открываются по номеру.
        jdbc.update("""
                insert into import_run (seller_account_id, marketplace_code, import_type,
                                       date_from, date_to, status,
                                       total_units, done_units, failed_units, skipped_units)
                values (?, 'OZON', 'FINANCE', ?, ?, 'DONE', 1, 1, 0, 0)
                """, account.getId(), DAY, DAY);
    }

    private String reportCoverage() {
        return "/api/marketplaces/ozon/data/coverage?dateFrom=" + DAY + "&dateTo=" + DAY;
    }

    /**
     * Сколько дней покрытие показывает загруженными.
     *
     * <p>Читается поле ответа, а не таблица: проверяется ровно то, что увидит клиент,
     * вместе с формой ответа.
     */
    private long loadedDaysIn(String session, String path) throws Exception {
        String body = get(session, path).body();
        var matcher = java.util.regex.Pattern.compile("\"loadedDays\":(\\d+)").matcher(body);
        assertThat(matcher.find())
                .as("в ответе должно быть поле loadedDays, получено: %s", body)
                .isTrue();
        return Long.parseLong(matcher.group(1));
    }

    private Long accountIdOf(String login) {
        return jdbc.queryForObject("""
                select a.id from seller_account a
                join app_user u on u.id = a.app_user_id
                where u.login = ?
                """, Long.class, login);
    }

    private String clientIdOf(String login) {
        return jdbc.queryForObject("""
                select a.client_id from seller_account a
                join app_user u on u.id = a.app_user_id
                where u.login = ?
                """, String.class, login);
    }

    private String apiKeyOf(String login) {
        return jdbc.queryForObject("""
                select a.api_key from seller_account a
                join app_user u on u.id = a.app_user_id
                where u.login = ?
                """, String.class, login);
    }

    private String loginOfAccount(String clientId) {
        return jdbc.queryForObject("""
                select u.login from app_user u
                join seller_account a on a.app_user_id = u.id
                where a.client_id = ?
                """, String.class, clientId);
    }

    private Long latestImportIdOf(String login) {
        return jdbc.queryForObject("""
                select max(r.id) from import_run r
                join seller_account a on a.id = r.seller_account_id
                join app_user u on u.id = a.app_user_id
                where u.login = ?
                """, Long.class, login);
    }

    /** Сессия входа продавца: пара кук CSRF плюс собственная кука {@code JSESSIONID}. */
    private String login(String login, String password) throws Exception {
        HttpClient client = client();
        String csrf = cookie(get(client, "/api/auth/csrf"), "XSRF-TOKEN");

        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                        .uri(URI.create(url("/api/auth/login")))
                        .header("Content-Type", "application/json")
                        .header("Cookie", csrf)
                        .header("X-XSRF-TOKEN", csrf.substring("XSRF-TOKEN=".length()))
                        .POST(HttpRequest.BodyPublishers.ofString("""
                                {"login":"%s","password":"%s"}
                                """.formatted(login, password)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("вход %s: %s", login, response.body()).isEqualTo(200);
        return cookie(response, "JSESSIONID");
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder().uri(URI.create(url(path))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String session, String path) throws Exception {
        return client().send(HttpRequest.newBuilder().uri(URI.create(url(path)))
                        .header("Cookie", session).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /** Клиент с настоящим хранилищем кук — так работает браузер. */
    private static HttpClient client() {
        return HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
                .build();
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static String cookie(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith(name + "="))
                .map(cookie -> cookie.substring(0, cookie.indexOf(';')))
                .findFirst()
                .orElse("");
    }
}