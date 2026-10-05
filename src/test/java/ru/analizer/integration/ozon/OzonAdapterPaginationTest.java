package ru.analizer.integration.ozon;

import org.junit.jupiter.api.Test;
import ru.analizer.integration.model.AccrualDto;
import ru.analizer.integration.model.AccrualPage;
import ru.analizer.account.domain.MarketplaceCredentials;
import ru.analizer.integration.ozon.dto.finance.FinanceAccrualByDayRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import ru.analizer.integration.ozon.OzonAdapter;
import ru.analizer.integration.ozon.OzonClient;
import ru.analizer.integration.ozon.OzonProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Пагинация {@code /v1/finance/accrual/by-day}.
 *
 * <p>У OZON нет ни limit, ни offset — только курсор {@code last_id}. Пустая строка означает
 * «день вычитан полностью». Дата между страницами обязана оставаться прежней, иначе OZON
 * отвечает 400, поэтому тест следит за тем, чтобы дата не «поехала».
 */
class OzonAdapterPaginationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 4, 10);

    /** Ключи подставные: настоящий HTTP не используется. */
    private static final MarketplaceCredentials CREDENTIALS =
            new MarketplaceCredentials("1154", "test-key");

    /** Заглушка клиента: отдаёт заранее заданные страницы и записывает запросы. */
    private static final class ScriptedClient extends OzonClient {

        private final List<List<AccrualDto>> pages = new ArrayList<>();
        private final List<FinanceAccrualByDayRequest> requests = new ArrayList<>();

        ScriptedClient() {
            super(RestClientFactory.noop(), OzonPropertiesFixture.configured());
        }

        void addPage(List<AccrualDto> accruals, String lastId) {
            pages.add(accruals);
            lastIds.add(lastId);
        }

        private final List<String> lastIds = new ArrayList<>();

        @Override
        public AccrualPage getAccrualsByDay(MarketplaceCredentials credentials, LocalDate date, String lastId) {
            requests.add(new FinanceAccrualByDayRequest(date.toString(), lastId == null ? "" : lastId));
            int index = requests.size() - 1;
            String next = index < lastIds.size() ? lastIds.get(index) : "";
            return new AccrualPage(pages.get(index), next);
        }
    }

    @Test
    void walksAllPagesUntilLastIdIsEmpty() {
        ScriptedClient client = new ScriptedClient();
        client.addPage(List.of(accrual(1L), accrual(2L)), "cursor-1");
        client.addPage(List.of(accrual(3L)), "cursor-2");
        client.addPage(List.of(accrual(4L), accrual(5L)), "");

        List<AccrualDto> result = new OzonAdapter(client).fetchAccrualsByDay(CREDENTIALS, DAY);

        assertThat(result).extracting(AccrualDto::externalId).containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(client.requests).hasSize(3);
        assertThat(client.requests.get(0).lastId()).as("первый запрос идёт без курсора").isEmpty();
        assertThat(client.requests.get(1).lastId()).isEqualTo("cursor-1");
        assertThat(client.requests.get(2).lastId()).isEqualTo("cursor-2");
    }

    @Test
    void keepsTheSameDateOnEveryPage() {
        ScriptedClient client = new ScriptedClient();
        client.addPage(List.of(accrual(1L)), "cursor-1");
        client.addPage(List.of(accrual(2L)), "");

        new OzonAdapter(client).fetchAccrualsByDay(CREDENTIALS, DAY);

        // OZON возвращает 400, если вместе с last_id передать другую дату.
        assertThat(client.requests).allSatisfy(r ->
                assertThat(r.date()).isEqualTo(DAY.toString()));
    }

    @Test
    void singlePageWithEmptyLastIdMeansTheDayIsComplete() {
        ScriptedClient client = new ScriptedClient();
        client.addPage(List.of(accrual(1L)), "");

        List<AccrualDto> result = new OzonAdapter(client).fetchAccrualsByDay(CREDENTIALS, DAY);

        assertThat(result).hasSize(1);
        assertThat(client.requests).hasSize(1);
    }

    @Test
    void dayWithoutAccrualsReturnsEmptyList() {
        ScriptedClient client = new ScriptedClient();
        client.addPage(List.of(), "");

        assertThat(new OzonAdapter(client).fetchAccrualsByDay(CREDENTIALS, DAY)).isEmpty();
    }

    private static AccrualDto accrual(long id) {
        return new AccrualDto(id, DAY, "unit-" + id, AccrualDto.Category.NON_ITEM, null,
                java.math.BigDecimal.ZERO, "RUB", null, null,
                new AccrualDto.FeeDetail(1, java.math.BigDecimal.ONE, "RUB"), null, "{}");
    }

    /** Значения таймаутов не влияют на тест, но конфигурация должна быть валидной. */
    private static final class OzonPropertiesFixture {
        static OzonProperties configured() {
            return new OzonProperties("https://api-seller.ozon.ru",
                Duration.ofSeconds(1), Duration.ofSeconds(1), 0, Duration.ZERO);
        }
    }
}
