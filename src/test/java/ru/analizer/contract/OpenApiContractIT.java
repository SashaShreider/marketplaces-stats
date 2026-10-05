package ru.analizer.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import ru.analizer.support.AbstractPostgresIntegrationTest;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контракт API не расходится с кодом.
 *
 * <p>Смысл проверки — в том, что она падает сама. Контракт лежит в репозитории файлом,
 * и его легко забыть обновить после добавления метода: всё компилируется, тесты зелёные,
 * а фронтенд генерирует типы по старому описанию и не знает про новый метод. Ошибка
 * всплывает в разработке фронтенда и выглядит как чужой баг.
 *
 * <p>Запрос идёт по настоящему HTTP, а не через MockMvc: так проверяется и сам
 * контракт, и то, что он отдаётся анонимно. С MockMvc это были бы две разные
 * проверки, а «отдаётся ли контракт без входа» — самая частая причина неудачи
 * фронтенда, потому что контракт нужен именно до входа.
 *
 * <p>Сравнение идёт по содержимому, а не по байтам: порядок ключей и отступы в
 * сгенерированном JSON меняются от версии к версии и ничего не говорят о расхождении
 * смысла. Поэтому JSON читается и раскладывается в упорядоченную карту.
 *
 * <p>Если контракт нужно обновить намеренно — прогнать класс с
 * {@code -Panalizer.updateOpenApi=true}. Вручную править JSON не нужно: так в него
 * попадёт не то, что реально отдаёт сервер.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiContractIT extends AbstractPostgresIntegrationTest {

    private static final Path CONTRACT = Path.of("docs", "openapi.json");

    /** Пересобрать файл контракта вместо проверки: -Panalizer.updateOpenApi=true. */
    private static final boolean UPDATE =
            Boolean.getBoolean("analizer.updateOpenApi");

    /**
     * Методы, которые обязаны быть в контракте.
     *
     * <p>Список задан явно и не выводится из кода: иначе проверка сообщала бы, что
     * контракт совпадает с самим собой, и пропускала бы пропажу метода. Сверить список
     * с правкой контроллера — намеренно ручная работа.
     *
     * <p>Имя параметра пути взято из контракта, а не из документации. Если переименовать
     * параметр в контроллере, springdoc обновит имя сам, а вот забытый здесь список
     * упал бы с внятным сообщением.
     */
    private static final List<String> REQUIRED_PATHS = List.of(
            "/api/auth/csrf",
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/logout",
            "/api/auth/me",
            "/api/marketplaces",
            "/api/marketplaces/{marketplace}",
            "/api/marketplaces/{marketplace}/credentials",
            "/api/marketplaces/{marketplace}/analytics/daily",
            "/api/marketplaces/{marketplace}/analytics/products",
            "/api/marketplaces/{marketplace}/imports",
            "/api/marketplaces/{marketplace}/imports/finance",
            "/api/marketplaces/{marketplace}/imports/catalog",
            "/api/marketplaces/{marketplace}/data/coverage",
            "/api/marketplaces/{marketplace}/data/catalog",
            "/api/marketplaces/{marketplace}/data/authors");

    @LocalServerPort
    int port;

    /** Свой, а не бина приложения: Boot 4 не публикует ObjectMapper автоматически,
     *  а нужен он здесь только для сравнения двух деревьев. */
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .build();

    private final HttpClient guest = HttpClient.newHttpClient();

    @Test
    @DisplayName("Контракт отдаётся без входа — по нему генерируются типы")
    void contractIsAvailableToAnonymous() throws Exception {
        HttpResponse<String> response = fetchContract();

        assertThat(response.statusCode())
                .as("контракт должен быть доступен до входа, иначе типы не сгенерировать. Ответ: %s",
                        response.body())
                .isEqualTo(200);
        assertThat(response.body()).as("пустой контракт бесполезен").contains("/api/auth/login");
    }

    @Test
    @DisplayName("В контракте есть все методы API")
    void contractDescribesAllEndpoints() throws Exception {
        @SuppressWarnings("unchecked")
        var paths = (Map<String, Object>) readContract().get("paths");

        List<String> missing = new ArrayList<>();
        for (String path : REQUIRED_PATHS) {
            if (!paths.containsKey(path)) {
                missing.add(path);
                continue;
            }
            @SuppressWarnings("unchecked")
            var operations = (Map<String, Object>) paths.get(path);
            if (operations.isEmpty()) {
                missing.add(path + " — операций нет");
            }
        }

        assertThat(paths).as("в контракте нет ни одного пути").isNotEmpty();
        assertThat(missing)
                .as("методы есть в коде, но их нет в контракте. "
                        + "Обновить: gradlew test --tests %s -Panalizer.updateOpenApi=true",
                        OpenApiContractIT.class.getSimpleName())
                .isEmpty();
    }

    @Test
    @DisplayName("Контракт описывает вход как куку, а не как токен")
    void contractDescribesSessionCookie() throws Exception {
        @SuppressWarnings("unchecked")
        var components = (Map<String, Object>) readContract().get("components");

        assertThat(components)
                .as("в контракте нет схем безопасности: сгенерированный клиент не поймёт, что вход — это кука")
                .isNotNull();
        assertThat(components.toString())
                .as("вход должен быть описан через куку JSESSIONID, "
                        + "иначе клиент отправит несуществующий заголовок Authorization")
                .contains("JSESSIONID");
    }

    @Test
    @DisplayName("Контракт в репозитории совпадает с тем, что отдаёт приложение")
    void contractMatchesCode() throws Exception {
        var actual = readContract();

        if (UPDATE) {
            writeContract(actual);
            assertThat(Files.exists(CONTRACT)).as("контракт записан").isTrue();
            return;
        }

        assertThat(Files.exists(CONTRACT))
                .as("нет файла %s. Собрать его: gradlew test --tests %s -Panalizer.updateOpenApi=true",
                        CONTRACT, OpenApiContractIT.class.getSimpleName())
                .isTrue();

        var expected = readContractFile();
        assertThat(actual)
                .as("контракт разошёлся с кодом: метод или его форма изменились, "
                        + "но docs/openapi.json не обновлён. "
                        + "Обновить: gradlew test --tests %s -Panalizer.updateOpenApi=true",
                        OpenApiContractIT.class.getSimpleName())
                .isEqualTo(expected);
    }

    private HttpResponse<String> fetchContract() throws Exception {
        return guest.send(
                HttpRequest.newBuilder().uri(URI.create(url("/v3/api-docs"))).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Контракт как сгенерированное приложением дерево, а не готовый файл.
     *
     * <p>Раздел {@code servers} выбрасывается: туда springdoc кладёт адрес сервера вместе
     * с портом, а в тестах порт случайный. Без этого проверка падала бы при каждом
     * прогоне, ничего не говоря о расхождении, — и её перестали бы читать.
     * Назначение адреса задаёт reverse proxy, а не этот проект, поэтому в контракте
     * ему не место.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> readContract() throws Exception {
        var contract = mapper.readValue(fetchContract().body(), Map.class);
        contract.remove("servers");
        return contract;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readContractFile() throws Exception {
        var contract = mapper.readValue(Files.readString(CONTRACT), Map.class);
        // Тот же раздел выбрасывается, что и при чтении ответа: в файле он был записан
        // с портом того прогона, который его создал.
        contract.remove("servers");
        return contract;
    }

    private void writeContract(Map<String, Object> contract) throws Exception {
        Files.createDirectories(CONTRACT.getParent());
        Files.writeString(CONTRACT,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(contract) + "\n");
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
