package ru.analizer.integration;

import org.springframework.context.annotation.Import;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.marketplace.AccrualTypeInfo;
import ru.analizer.marketplace.AccrualDto;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Справочник типов начислений против реального ответа {@code /v1/finance/accrual/types}.
 *
 * <p>Смысл теста не в количестве строк, а в проверке, что справочник приходит из API,
 * а не захардкожен: при смене имён OZON аналитика обязана увидеть новое значение, а
 * бизнес-логика — не опираться на конкретные {@code type_id}.
 */
@Import(FixtureAdapterConfig.class)
class AccrualTypeDictionaryIT extends AbstractPostgresIntegrationTest {

    private static final String DAY_2026_04_10 = "example-2026-04-10.json";
    private static final String DAY_2026_09_26 = "fixtures/accruals-2026-09-26-full.json";

    @Test
    @DisplayName("Справочник загружается целиком из ответа API")
    void loadsDictionaryFromApi() {
        List<AccrualTypeInfo> remote = FixtureAdapters.types();

        assertThat(remote).as("реальный справочник OZON на 2026-09-30").hasSize(132);

        syncService.syncAccrualTypes();

        assertThat(count("accrual_type")).isEqualTo(132);
        assertThat(jdbc.queryForObject(
                "select name from accrual_type where external_type_id = 74", String.class))
                .isEqualTo("StarsMembership");
        assertThat(jdbc.queryForObject(
                "select name from accrual_type where external_type_id = 69", String.class))
                .as("SaleCommission понадобится аналитике на этапе расчёта комиссии")
                .isEqualTo("SaleCommission");
    }

    @Test
    @DisplayName("Каждый применённый type_id есть в справочнике — расходы не останутся безымянными")
    void everyAppliedTypeExistsInDictionary() {
        List<AccrualTypeInfo> remote = FixtureAdapters.types();
        Set<Integer> known = remote.stream().map(AccrualTypeInfo::externalId).collect(Collectors.toSet());

        Set<Integer> applied = new TreeSet<>();
        for (String day : List.of(DAY_2026_04_10, DAY_2026_09_26)) {
            for (AccrualDto dto : FixtureAdapters.parse(day)) {
                if (dto.nonItemFee() != null) {
                    applied.add(dto.nonItemFee().typeId());
                }
                if (dto.itemFees() != null) {
                    dto.itemFees().stream()
                            .flatMap(f -> f.fees().stream())
                            .forEach(d -> applied.add(d.typeId()));
                }
                if (dto.posting() != null) {
                    dto.posting().products().stream()
                            .flatMap(p -> p.deliveryServices().stream())
                            .forEach(d -> applied.add(d.typeId()));
                }
            }
        }

        assertThat(applied).as("в обоих реальных днях встречаются эти типы").isNotEmpty();
        assertThat(applied).as("типы без записи в справочнике означали бы потерю информации")
                .isSubsetOf(known);
    }

    @Test
    @DisplayName("Названия типов приходят из API, а не из кода приложения")
    void namesComeFromApiNotFromCode() {
        syncService.syncAccrualTypes();

        Map<Integer, String> byExternalId = FixtureAdapters.types().stream()
                .collect(Collectors.toMap(AccrualTypeInfo::externalId, AccrualTypeInfo::name));

        // Названия, которые в реальности означают совсем не то, что можно было бы угадать.
        // Именно поэтому список type_id нельзя зашивать в код.
        assertThat(byExternalId.get(16)).isEqualTo("Drop-Off");
        assertThat(byExternalId.get(29)).isEqualTo("LastMileCourier");
        assertThat(byExternalId.get(32)).isEqualTo("Logistic");
        assertThat(byExternalId.get(59)).isEqualTo("ReturnFlowLogistic");
        assertThat(byExternalId.get(98)).isEqualTo("DeliveryToHandoverPlaceByOzon");

        for (Map.Entry<Integer, String> entry : byExternalId.entrySet()) {
            assertThat(jdbc.queryForObject(
                    "select name from accrual_type where external_type_id = ?", String.class, entry.getKey()))
                    .as("тип %s должен называться как в API", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    @DisplayName("Справочник привязан к маркетплейсу, а не к конкретной БД")
    void dictionaryIsScopedToMarketplace() {
        syncService.syncAccrualTypes();

        assertThat(jdbc.queryForObject("""
                select count(*) from accrual_type a
                join marketplace m on m.id = a.marketplace_id
                where m.code = 'OZON'
                """, Long.class)).isEqualTo(132L);
        assertThat(jdbc.queryForObject("select count(*) from marketplace", Long.class))
                .as("для одного маркетплейса достаточно одной строки справочника")
                .isEqualTo(1L);
    }
}