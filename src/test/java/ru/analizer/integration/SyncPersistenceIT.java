package ru.analizer.integration;

import org.springframework.context.annotation.Import;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.analizer.sync.SyncReport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Контрольная точка IMPLEMENTATION §19 Этап 4: реальные данные OZON попали в PostgreSQL
 * без потерь. Сверяются все поля, перечисленные в документе.
 */
@Import(FixtureAdapterConfig.class)
class SyncPersistenceIT extends AbstractPostgresIntegrationTest {

    private static final String DAY_2026_04_10 = "example-2026-04-10.json";

    private SyncReport sync() {
        FixtureAdapters.FIXTURES.put(DAY, DAY_2026_04_10);
        syncService.syncAccrualTypes();
        return syncService.sync(CLIENT_ID, DAY, DAY);
    }

    @Test
    @DisplayName("Синхронизация дня: количество операций и отчёт")
    void syncsWholeDay() {
        SyncReport report = sync();

        assertThat(report.requestedDays()).isEqualTo(1);
        assertThat(report.syncedDays()).isEqualTo(1);
        assertThat(report.complete()).isTrue();
        assertThat(report.accrualsReceived()).isEqualTo(94);
        assertThat(report.accrualsInserted()).isEqualTo(94);
        assertThat(report.accrualsUpdated()).isZero();
        assertThat(report.accrualsSkipped()).isZero();
        assertThat(count("finance_accrual")).isEqualTo(94);
    }

    @Test
    @DisplayName("Справочник типов начислений загружен из API, а не захардкожен")
    void loadsAccrualTypeDictionary() {
        sync();

        // Реальный ответ /v1/finance/accrual/types на 2026-09-30 содержит 132 типа.
        // Число намеренно не «прибито» меньшим: оно придёт из следующего ответа API.
        assertThat(count("accrual_type")).isEqualTo(132);
        assertThat(jdbc.queryForObject(
                "select name from accrual_type where external_type_id = 74", String.class))
                .isEqualTo("StarsMembership");
        assertThat(jdbc.queryForObject(
                "select name from accrual_type where external_type_id = 1", String.class))
                .isEqualTo("Acquiring");
        // Название пришло из API, а не из кода: угадать его было бы невозможно.
        assertThat(jdbc.queryForObject(
                "select name from accrual_type where external_type_id = 32", String.class))
                .isEqualTo("Logistic");
    }

    @Test
    @DisplayName("accrual_id, дата, unit_number, категория и сумма сохранены верно")
    void savesAccrualHeaderFields() {
        sync();

        var row = jdbc.queryForMap("""
                select external_id, accrual_date, unit_number, accrued_category, total_amount, currency
                from finance_accrual where external_id = 48762627746
                """);
        assertThat(row.get("external_id")).isEqualTo(48762627746L);
        // JDBC отдаёт java.sql.Date, поэтому сравниваем строковое представление ISO.
        assertThat(row.get("accrual_date").toString()).isEqualTo("2026-04-10");
        assertThat(row.get("unit_number")).isEqualTo("31020767-0597-1");
        assertThat(row.get("accrued_category")).isEqualTo("POSTING");
        assertThat((java.math.BigDecimal) row.get("total_amount")).isEqualByComparingTo("869.30");
        assertThat(row.get("currency")).isEqualTo("RUB");
    }

    @Test
    @DisplayName("unit_number не уникален: одна продажа — несколько финансовых операций")
    void keepsSeveralAccrualsPerUnitNumber() {
        sync();

        assertThat(jdbc.queryForObject("""
                select count(*) from finance_accrual where unit_number = '04142187-0153-1'
                """, Long.class)).isEqualTo(4L);
        // Сумма операций одного платежа = его вклад в выплату.
        assertThat(jdbc.queryForObject("""
                select sum(total_amount) from finance_accrual where unit_number = '04142187-0153-1'
                """, java.math.BigDecimal.class)).isEqualByComparingTo("570.77");
    }

    @Test
    @DisplayName("Исходный JSON каждой операции сохранён в raw_data без потери значений")
    void keepsRawJson() {
        sync();

        // JSONB нормализует пробелы и порядок ключей, поэтому сравниваем не текст,
        // а содержимое: сохранённый JSONB должен быть равен исходному элементу массива.
        String stored = jdbc.queryForObject(
                "select raw_data::text from finance_accrual where external_id = 48762627746", String.class);
        String original = FixtureAdapters.parse(DAY_2026_04_10).stream()
                .filter(a -> a.externalId() == 48762627746L)
                .findFirst().orElseThrow()
                .rawJson();

        assertThat(FixtureAdapters.sameJson(stored, original))
                .as("raw_data должен содержать те же значения, что прислал OZON")
                .isTrue();

        // И содержимое доступно для запросов прямо в БД.
        assertThat(jdbc.queryForObject("""
                select raw_data->'posting'->>'delivery_schema' from finance_accrual
                where external_id = 48762627746
                """, String.class)).isEqualTo("Fbo");
        assertThat(jdbc.queryForObject("""
                select raw_data->'posting'->'products'->0->'commission'->>'commission_ratio'
                from finance_accrual where external_id = 48762627746
                """, String.class)).isEqualTo("value:\"0.390000\"");

        assertThat(jdbc.queryForObject("""
                select count(*) from finance_accrual where raw_data is null
                """, Long.class)).isZero();
    }

    @Test
    @DisplayName("POSTING: товары, количество, цены, скидки и бонусы")
    void savesPostingProducts() {
        sync();

        assertThat(count("posting")).isEqualTo(26);

        var row = jdbc.queryForMap("""
                select p.sku, p.quantity, p.seller_price, p.sale_price, p.sale_amount,
                       p.sale_commission, p.commission, p.commission_ratio, p.coinvestment,
                       p.bonus, p.currency, po.delivery_schema
                from posting_product p
                join posting po on po.id = p.posting_id
                join finance_accrual a on a.id = po.finance_accrual_id
                where a.external_id = 48762627746
                """);
        assertThat(row.get("sku")).isEqualTo(1388985440L);
        assertThat(row.get("quantity")).isEqualTo(1);
        assertThat((java.math.BigDecimal) row.get("seller_price")).isEqualByComparingTo("1530");
        assertThat((java.math.BigDecimal) row.get("sale_price")).isEqualByComparingTo("871.43");
        assertThat((java.math.BigDecimal) row.get("sale_amount")).isEqualByComparingTo("1530");
        // Комиссия хранится со знаком минус.
        assertThat((java.math.BigDecimal) row.get("sale_commission")).isEqualByComparingTo("-596.70");
        assertThat((java.math.BigDecimal) row.get("commission")).isEqualByComparingTo("-596.70");
        assertThat(row.get("commission_ratio")).isEqualTo("0.390000");
        assertThat((java.math.BigDecimal) row.get("coinvestment")).isEqualByComparingTo("8.71");
        assertThat((java.math.BigDecimal) row.get("bonus")).isEqualByComparingTo("649.86");
        assertThat(row.get("currency")).isEqualTo("RUB");
        assertThat(row.get("delivery_schema")).isEqualTo("Fbo");
    }

    @Test
    @DisplayName("POSTING без комиссии: начисление только по логистике")
    void savesPostingWithoutCommission() {
        sync();

        assertThat(jdbc.queryForObject("""
                select count(*) from posting_product where commission is null
                """, Long.class)).isEqualTo(6L);
        // Такие операции не теряются: товар и логистика на месте.
        assertThat(jdbc.queryForObject("""
                select count(*) from posting_product p
                join posting po on po.id = p.posting_id
                join finance_accrual a on a.id = po.finance_accrual_id
                where a.external_id = 48751325983 and p.sale_price is null
                """, Long.class)).isEqualTo(1L);
    }

    @Test
    @DisplayName("Логистика: каждая услуга сохранена отдельной строкой")
    void savesDeliveryServices() {
        sync();

        assertThat(jdbc.queryForObject("""
                select count(*) from delivery_service d
                join posting_product p on p.id = d.posting_product_id
                join posting po on po.id = p.posting_id
                join finance_accrual a on a.id = po.finance_accrual_id
                where a.external_id = 48762627746
                """, Long.class)).isEqualTo(2L);

        assertThat(jdbc.queryForObject("""
                select sum(d.amount) from delivery_service d
                join posting_product p on p.id = d.posting_product_id
                join posting po on po.id = p.posting_id
                join finance_accrual a on a.id = po.finance_accrual_id
                where a.external_id = 48762627746
                """, java.math.BigDecimal.class)).isEqualByComparingTo("-64.00");
    }

    @Test
    @DisplayName("ITEM: расходы по SKU с детализацией по типам")
    void savesItemFees() {
        sync();

        assertThat(count("item_fee")).isEqualTo(53);
        assertThat(count("item_fee_detail")).isEqualTo(53);

        assertThat(jdbc.queryForObject("""
                select count(*) from item_fee f
                join finance_accrual a on a.id = f.finance_accrual_id
                where a.external_id = 48762253857
                """, Long.class)).isEqualTo(1L);

        assertThat(jdbc.queryForObject("""
                select d.type_id from item_fee_detail d
                join item_fee f on f.id = d.item_fee_id
                join finance_accrual a on a.id = f.finance_accrual_id
                where a.external_id = 48762253857
                """, Integer.class)).isEqualTo(1);

        assertThat(jdbc.queryForObject("""
                select d.amount from item_fee_detail d
                join item_fee f on f.id = d.item_fee_id
                join finance_accrual a on a.id = f.finance_accrual_id
                where a.external_id = 48762253857
                """, java.math.BigDecimal.class)).isEqualByComparingTo("-14.75");
    }

    @Test
    @DisplayName("NON_ITEM: расходы без SKU сохранены и не привязаны к товарам")
    void savesNonItemFees() {
        sync();

        assertThat(count("non_item_fee")).isEqualTo(15);

        var row = jdbc.queryForMap("""
                select n.type_id, n.amount, a.unit_number
                from non_item_fee n
                join finance_accrual a on a.id = n.finance_accrual_id
                where a.external_id = 48822624708
                """);
        assertThat(row.get("type_id")).isEqualTo(46);
        assertThat((java.math.BigDecimal) row.get("amount")).isEqualByComparingTo("-380.68");
        // unit_number у этого начисления отсутствует — это норма, не потеря данных.
        assertThat(row.get("unit_number")).isNull();
    }

    @Test
    @DisplayName("Продажи и возвраты сохраняют знак: sale_price отрицательный при возврате")
    void keepsSignsForSalesAndReturns() {
        sync();

        assertThat(jdbc.queryForObject(
                "select count(*) from posting_product where sale_price > 0", Long.class))
                .isEqualTo(19L);
        assertThat(jdbc.queryForObject(
                "select count(*) from posting_product where sale_price < 0", Long.class))
                .isEqualTo(1L);
        // При возврате комиссия возвращается, то есть положительна.
        assertThat(jdbc.queryForObject(
                "select sale_commission from posting_product where sale_price < 0",
                java.math.BigDecimal.class)).isEqualByComparingTo("672.36");
    }

    @Test
    @DisplayName("Сумма всех операций за день сходится с ручным подсчётом")
    void totalMatchesManualCalculation() {
        sync();

        sumTotal("finance_accrual").is("11297.23");
    }

    @Test
    @DisplayName("Аккаунт продавца создан и связан с маркетплейсом OZON")
    void createsSellerAccount() {
        sync();

        assertThat(count("seller_account")).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select s.client_id || '|' || m.code from seller_account s
                join marketplace m on m.id = s.marketplace_id
                """, String.class)).isEqualTo("1154|OZON");
    }

    @Test
    @DisplayName("API-ключ не сохраняется в базе")
    void neverStoresApiKey() {
        sync();

        var columns = jdbc.queryForList("""
                select column_name from information_schema.columns
                where table_name = 'seller_account'
                """, String.class);
        assertThat(columns).noneMatch(c -> c.toLowerCase().contains("api_key"));
    }

    @Test
    @DisplayName("День без начислений не создаёт строк")
    void emptyDayCreatesNothing() {
        FixtureAdapters.FIXTURES.clear();
        SyncReport report = syncService.sync(CLIENT_ID, offsetDay(1), offsetDay(1));

        assertThat(report.accrualsReceived()).isZero();
        assertThat(count("finance_accrual")).isZero();
    }

    private static java.time.LocalDate offsetDay(int days) {
        return DAY.plusDays(days);
    }
}