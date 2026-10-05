package ru.analizer.analytics.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import ru.analizer.analytics.domain.FeeFact;
import ru.analizer.analytics.domain.FinancialModel;

/**
 * Выборки каталога товаров для отчёта и подсказок фильтра.
 *
 * <p>Запросы написаны на SQL, а не на JPQL, намеренно: фильтр по автору идёт по
 * массивам {@code author_keys} и {@code author_surnames}, а выражение с {@code unnest}
 * на JPQL не выражается. Финансовые правила при этом не дублируются — агрегаты
 * считает {@link FinancialModel}, те же самые, что и в дневном отчёте.
 *
 * <p>Именованные параметры требуют именно {@link NamedParameterJdbcTemplate}: обычный
 * {@code JdbcTemplate} передал бы {@code Map} как один параметр, и PostgreSQL ответил бы
 * «Расширение hstore не установлено».
 */
@Repository
public class CatalogFacts {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    public CatalogFacts(JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = namedJdbc;
    }

    /**
     * Страница каталога с фильтрами.
     *
     * <p>Все товары аккаунта, а не только проданные: ноль у товара, который есть в
     * каталоге, — честный ответ «продаж не было». Если брать только товары из
     * начислений, отчёт молчал бы о том, что перестало продаваться.
     *
     * <p>Сортировка по названию: доход считается в памяти, чтобы правила совпали с
     * дневным отчётом, и в SQL он неизвестен. Поэтому порядок по доходу применяется
     * после сведения, а постраничная выборка каталога идёт по названию.
     *
     * <p>Условие по автору одно, а не два: срабатывает ЛИБО совпадение по полному ключу,
     * ЛИБО по фамилии. Иначе ввод «Сурцуков» не нашёл бы ничего — ключ из одного слова
     * это «сурцуков», а в {@code author_keys} лежит «сурцуков а.», и при проверке обоих
     * условий сразу не сходилось бы ни то, ни другое.
     *
     * @param authorKey сведённое имя «фамилия инициалы»; пустая строка — без фильтра
     * @param authorSurname фамилия; пустая строка — без фильтра
     * @param query подстрока названия, артикула или ISBN; пустая строка — без фильтра
     */
    public List<ProductCatalogRow> productsPage(Long accountId, String authorKey,
                                                String authorSurname, String query,
                                                int offset, int limit) {
        RowMapper<ProductCatalogRow> mapper = (rs, i) -> new ProductCatalogRow(
                rs.getLong("id"),
                rs.getLong("sku"),
                rs.getString("offer_id"),
                rs.getString("name"),
                rs.getString("primary_image"),
                rs.getString("isbn"),
                rs.getLong("type_id"));
        return namedJdbc.query("""
                select id, sku, offer_id, name, primary_image, isbn, type_id
                from ozon_product
                where seller_account_id = :account
                  and (cast(:authorKey as text) = ''
                       or :authorKey = any(author_keys)
                       or :authorSurname = any(author_surnames))
                  and (cast(:queryText as text) = ''
                       or lower(name) like lower('%' || :queryText || '%')
                       or lower(coalesce(offer_id, '')) like lower('%' || :queryText || '%')
                       or lower(coalesce(isbn, '')) like lower('%' || :queryText || '%'))
                order by name, sku
                offset :offset limit :limit
                """, filterParams(accountId, authorKey, authorSurname, query)
                        .addValue("offset", offset)
                        .addValue("limit", limit), mapper);
    }

    /** Сколько товаров подходит под фильтры — чтобы клиент знал число страниц. */
    public long productsCount(Long accountId, String authorKey, String authorSurname, String query) {
        Long count = namedJdbc.queryForObject("""
                select count(*) from ozon_product
                where seller_account_id = :account
                  and (cast(:authorKey as text) = ''
                       or :authorKey = any(author_keys)
                       or :authorSurname = any(author_surnames))
                  and (cast(:queryText as text) = ''
                       or lower(name) like lower('%' || :queryText || '%')
                       or lower(coalesce(offer_id, '')) like lower('%' || :queryText || '%')
                       or lower(coalesce(isbn, '')) like lower('%' || :queryText || '%'))
                """, filterParams(accountId, authorKey, authorSurname, query), Long.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource filterParams(Long accountId, String authorKey,
                                                      String authorSurname, String query) {
        return new MapSqlParameterSource("account", accountId)
                .addValue("authorKey", nullToEmpty(authorKey))
                .addValue("authorSurname", nullToEmpty(authorSurname))
                .addValue("queryText", nullToEmpty(query));
    }

    /**
     * Авторы товаров — исходными значениями, как их вернул OZON.
     *
     * <p>Показываем {@code author_raw}, а не сведённый ключ: пользователь должен видеть
     * то, что ввёл продавец, иначе не сможет проверить, кого мы имеем в виду.
     */
    public Map<Long, List<ProductAuthorView>> authorsFor(Long accountId, List<Long> skus) {
        Map<Long, List<ProductAuthorView>> result = new LinkedHashMap<>();
        if (skus == null || skus.isEmpty()) {
            return result;
        }
        jdbc.query("""
                select sku, author_raw, source, is_primary
                from product_author
                where seller_account_id = ? and sku = any(?)
                order by sku, is_primary desc, position
                """, rs -> {
            long sku = rs.getLong("sku");
            result.computeIfAbsent(sku, k -> new ArrayList<>())
                    .add(new ProductAuthorView(
                            rs.getString("author_raw"),
                            rs.getString("source"),
                            rs.getBoolean("is_primary")));
        }, accountId, skus.toArray(new Long[0]));
        return result;
    }

    /** Варианты авторов для подсказки фильтра — из данных продавца, а не из догадок. */
    public List<String> authorKeys(Long accountId) {
        return jdbc.query("""
                select distinct k
                from ozon_product, unnest(author_keys) as k
                where seller_account_id = ?
                order by k
                """, (rs, i) -> rs.getString(1), accountId);
    }

    /** Фамилии авторов — для широкого фильтра по одной фамилии. */
    public List<String> authorSurnames(Long accountId) {
        return jdbc.query("""
                select distinct s
                from ozon_product, unnest(author_surnames) as s
                where seller_account_id = ?
                order by s
                """, (rs, i) -> rs.getString(1), accountId);
    }

    /**
     * Расходы, у которых OZON не указал SKU.
     *
     * <p>NON_ITEM и CONTAINER приходят без товара, поэтому распределить их между
     * товарами можно только придумав правило. Отчёт их не распределяет и показывает
     * отдельно — иначе сумма расходов по товарам не совпала бы с дневным отчётом, и
     * непонятно было бы, где ошибка.
     */
    public List<FeeFact> unallocatedFees(Long accountId, LocalDate from, LocalDate to) {
        List<FeeFact> fees = new ArrayList<>();
        fees.addAll(jdbc.query("""
                select a.accrual_date, a.unit_number, a.external_id, n.type_id, n.amount
                from finance_accrual a
                join non_item_fee n on n.finance_accrual_id = a.id
                where a.seller_account_id = ? and a.accrual_date between ? and ?
                """, (rs, i) -> new FeeFact(
                rs.getObject("accrual_date", LocalDate.class),
                rs.getString("unit_number"),
                rs.getLong("external_id"),
                null,
                (Integer) rs.getObject("type_id"),
                rs.getBigDecimal("amount"),
                FeeFact.FeeKind.NON_ITEM), accountId, from, to));

        fees.addAll(jdbc.query("""
                select a.accrual_date, a.unit_number, a.external_id, c.type_id, c.amount
                from finance_accrual a
                join container_fee c on c.finance_accrual_id = a.id
                where a.seller_account_id = ? and a.accrual_date between ? and ?
                """, (rs, i) -> new FeeFact(
                rs.getObject("accrual_date", LocalDate.class),
                rs.getString("unit_number"),
                rs.getLong("external_id"),
                null,
                (Integer) rs.getObject("type_id"),
                rs.getBigDecimal("amount"),
                FeeFact.FeeKind.CONTAINER), accountId, from, to));
        return fees;
    }

    /** Количество проданных единиц по SKU за период. */
    public Map<Long, Integer> quantities(Long accountId, LocalDate from, LocalDate to) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        jdbc.query("""
                select p.sku, sum(p.quantity)
                from finance_accrual a
                join posting po on po.finance_accrual_id = a.id
                join posting_product p on p.posting_id = po.id
                where a.seller_account_id = ? and a.accrual_date between ? and ?
                group by p.sku
                """, rs -> {
            // sum() в PostgreSQL возвращает bigint, поэтому приводить через
            // (Integer) нельзя — будет ClassCastException на Long.
            Object value = rs.getObject(2);
            int quantity = value == null ? 0 : ((Number) value).intValue();
            result.merge(rs.getLong("sku"), quantity, Integer::sum);
        }, accountId, from, to);
        return result;
    }

    /** Сколько операций затронуло SKU — чтобы отличать одну крупную продажу от многих мелких. */
    public Map<Long, Integer> accrualCounts(Long accountId, LocalDate from, LocalDate to) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        jdbc.query("""
                select s.sku, count(*)
                from (
                    select p.sku as sku, a.external_id as acc
                    from finance_accrual a
                    join posting po on po.finance_accrual_id = a.id
                    join posting_product p on p.posting_id = po.id
                    where a.seller_account_id = ? and a.accrual_date between ? and ?
                    union
                    select f.sku as sku, a.external_id as acc
                    from finance_accrual a
                    join item_fee f on f.finance_accrual_id = a.id
                    where a.seller_account_id = ? and a.accrual_date between ? and ?
                ) s
                group by s.sku
                """, rs -> {
            result.merge(rs.getLong("sku"), rs.getInt(2), Integer::sum);
        }, accountId, from, to, accountId, from, to);
        return result;
    }

    /**
     * SKU, которые есть в начислениях, но отсутствуют в каталоге.
     *
     * <p>Показываются в отчёте отдельным списком. Молчать о них нельзя: суммы отчёта
     * по товарам разойдутся с дневным отчётом, и без объяснения непонятно, откуда взялась
     * разница.
     */
    public List<Long> skusMissingFromCatalog(Long accountId, LocalDate from, LocalDate to) {
        return jdbc.queryForList("""
                select distinct s.sku
                from (
                    select p.sku as sku
                from finance_accrual a
                join posting po on po.finance_accrual_id = a.id
                join posting_product p on p.posting_id = po.id
                where a.seller_account_id = ? and a.accrual_date between ? and ?
                    union
                    select f.sku as sku
                    from finance_accrual a
                    join item_fee f on f.finance_accrual_id = a.id
                    where a.seller_account_id = ? and a.accrual_date between ? and ?
                ) s
                where not exists (
                    select 1 from ozon_product op
                    where op.seller_account_id = ? and op.sku = s.sku
                )
                order by s.sku
                """, Long.class, accountId, from, to, accountId, from, to, accountId);
    }

    /**
     * Когда последний раз синхронизировался каталог.
     *
     * <p>Читается через {@code getTimestamp}, а не {@code getObject(…, Instant.class)}:
     * драйвер не умеет приводить {@code timestamptz} прямо в {@code Instant} и отвечает
     * «Преобразование из timestamptz в Instant не поддерживается».
     */
    public Instant lastCatalogSync(Long accountId) {
        List<Instant> values = jdbc.query(
                "select max(last_synced_at) from ozon_product where seller_account_id = ?",
                (rs, i) -> {
                    java.sql.Timestamp value = rs.getTimestamp(1);
                    return value == null ? null : value.toInstant();
                }, accountId);
        return values.isEmpty() ? null : values.getFirst();
    }

    /** Строка каталога для отчёта. */
    public record ProductCatalogRow(
            long id,
            long sku,
            String offerId,
            String name,
            String primaryImage,
            String isbn,
            Long typeId
    ) {
    }

    /**
     * Автор в исходном виде.
     *
     * @param source DECLARED — из карточки товара, COVER — с обложки
     */
    public record ProductAuthorView(String raw, String source, boolean primary) {
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}