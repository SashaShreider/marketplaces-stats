package ru.analizer.analytics.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.analizer.analytics.domain.FeeFact;
import ru.analizer.analytics.domain.FinancialModel;
import ru.analizer.analytics.infrastructure.filter.ProductAttributeFilter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Выборки каталога товаров для отчёта и подсказок фильтра.
 *
 * <p>Запросы написаны на SQL, а не на JPQL, намеренно: условия фильтров собирают
 * {@link ru.analizer.analytics.infrastructure.filter.ProductAttributeFilter}, а
 * подзапросы и массивы на JPQL не выражаются. Финансовые правила при этом не
 * дублируются — агрегаты считает {@link FinancialModel}, те же самые, что и в
 * дневном отчёте.
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
     * Страница каталога с фильтрами и заданной сортировкой.
     *
     * <p>Все товары аккаунта, а не только проданные: ноль у товара, который есть в
     * каталоге, — честный ответ «продаж не было». Если брать только товары из
     * начислений, отчёт молчал бы о том, что перестало продаваться.
     *
     * <p><b>Сортировка по доходу делается здесь, а не в памяти.</b> Постраничная выборка
     * и порядок строк — одно и то же: сначала выбирается страница, потом она
     * упорядочивается. Если упорядочить после выборки, «по доходу» покажет лучшие
     * товары той страницы, на которой они случайно оказались, а лучший продавец
     * окажется на последней странице.
     *
     * <p>Сумма дохода повторяет формулу {@code FinancialSummary.income()} — продажи плюс
     * возвраты плюс программа партнёров. Здесь она нужна только чтобы упорядочить строки;
     * числа в ответе по-прежнему считает {@link FinancialModel}, и тест сверяет, что
     * порядок из SQL совпадает с порядком по этим числам.
     *
     * @param filters фильтры по атрибутам; незаданные ничего не ограничивают
     * @param query   подстрока названия, артикула или ISBN; пустая строка — без фильтра
     * @param sort    {@code INCOME}, {@code NAME} или {@code SKU}
     */
    public List<ProductCatalogRow> productsPage(Long accountId, List<ProductAttributeFilter> filters,
                                                String query,
                                                LocalDate from, LocalDate to,
                                                String sort, int offset, int limit) {
        RowMapper<ProductCatalogRow> mapper = (rs, i) -> new ProductCatalogRow(
                rs.getLong("id"),
                rs.getLong("sku"),
                rs.getString("offer_id"),
                rs.getString("name"),
                rs.getString("primary_image"),
                rs.getString("isbn"),
                rs.getLong("type_id"));
        // ORDER BY подставляется по метке, а не склейкой блоков: склейка text block'ов
        // однажды съела перевод строки и дала «order by …offset».
        String sql = """
                with income as (
                    select p.sku,
                           sum(coalesce(p.sale_price, 0)
                               + coalesce(p.bonus, 0)
                               + coalesce(p.coinvestment, 0)) as total
                    from finance_accrual a
                    join posting po on po.finance_accrual_id = a.id
                    join posting_product p on p.posting_id = po.id
                    where a.seller_account_id = :account
                      and a.accrual_date between :from and :to
                    group by p.sku
                )
                select pr.id, pr.sku, pr.offer_id, pr.name, pr.primary_image,
                       pr.isbn, pr.type_id
                from ozon_product pr
                left join income i on i.sku = pr.sku
                where pr.seller_account_id = :account
                  and (cast(:queryText as text) = ''
                       or lower(pr.name) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.offer_id, '')) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.isbn, '')) like lower('%' || :queryText || '%'))
                /*FILTERS*/
                /*ORDER_BY*/
                offset :offset limit :limit
                """
                .replace("/*FILTERS*/", filterSql(filters, "pr"))
                .replace("/*ORDER_BY*/", orderBy(sort));

        return namedJdbc.query(sql, filterParams(accountId, filters, query)
                        .addValue("from", from)
                        .addValue("to", to)
                        .addValue("offset", offset)
                        .addValue("limit", limit), mapper);
    }

    /**
     * Складывает условия фильтров в одну строку {@code and …}.
     *
     * <p>Условия соединяются через AND, а не OR: фильтр по издательству и фильтр по
     * автору должны сужать выборку, а не расширять её.
     *
     * @return пустая строка, если ни один фильтр ничего не ограничивает
     */
    private static String filterSql(List<ProductAttributeFilter> filters, String alias) {
        if (filters == null || filters.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder();
        for (ProductAttributeFilter filter : filters) {
            String predicate = filter.predicate(alias);
            if (predicate == null || predicate.isBlank()) {
                continue;
            }
            sql.append("\n  and ").append(predicate);
        }
        return sql.toString();
    }

    /**
     * Предложение {@code ORDER BY} по ключу сортировки.
     *
     * <p>Ключ подставляется только из этого списка, а не из значения запроса: иначе
     * подставил бы его прямо в SQL. Второй элемент — SKU — всегда добавляется, чтобы
     * порядок был воспроизводимым: без него товары с одинаковым доходом или названием
     * приходили бы в произвольном порядке и между страницами «прыгали».
     */
    private static String orderBy(String sort) {
        String key = sort == null ? "INCOME" : sort.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (key) {
            case "NAME" -> "order by pr.name, pr.sku";
            case "SKU" -> "order by pr.sku";
            // Товар без начислений получает доход 0 и уходит в конец, а не пропадает:
            // в каталоге он есть, и молчать о нём значило бы скрыть товар без продаж.
            default -> "order by coalesce(i.total, 0) desc, pr.sku";
        };
    }

    /** Сколько товаров подходит под фильтры — чтобы клиент знал число страниц. */
    public long productsCount(Long accountId, List<ProductAttributeFilter> filters, String query) {
        Long count = namedJdbc.queryForObject("""
                select count(*) from ozon_product pr
                where pr.seller_account_id = :account
                  and (cast(:queryText as text) = ''
                       or lower(pr.name) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.offer_id, '')) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.isbn, '')) like lower('%' || :queryText || '%'))
                /*FILTERS*/
                """.replace("/*FILTERS*/", filterSql(filters, "pr")),
                filterParams(accountId, filters, query), Long.class);
        return count == null ? 0 : count;
    }

    private static MapSqlParameterSource filterParams(Long accountId,
                                                      List<ProductAttributeFilter> filters,
                                                      String query) {
        MapSqlParameterSource params = new MapSqlParameterSource("account", accountId)
                .addValue("queryText", nullToEmpty(query));
        for (ProductAttributeFilter filter : filters == null ? List.<ProductAttributeFilter>of() : filters) {
            params.addValues(filter.parameters());
        }
        return params;
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

    /**
     * Варианты авторов для подсказки фильтра — из данных продавца, а не из догадок.
     *
     * <p>Отдаём {@code author_raw}: те же строки, по которым и ищем. Если привести их
     * к другому виду, значение из подсказки не совпало бы с тем, что лежит в базе, и
     * фильтр молча вернул бы пустой список. Поэтому список может содержать «Сурцуков
     * А.», «Сурцуков Анатолий» и «А.В. Сурцуков» рядом — это три разных написания
     * одного продавца, и привести их к одному может только он сам, в карточках.
     */
    public List<String> authorValues(Long accountId) {
        return jdbc.query("""
                select distinct author_raw
                from product_author
                where seller_account_id = ?
                order by author_raw
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

    /**
     * Количество проданных и возвращённых единиц по SKU за период.
     *
     <p>Продажи и возвраты считаются разными запросами внутри одного SQL — фильтрами по
     знаку {@code sale_price}. Разделять в Java пришлось бы двумя проходами с разной
     агрегацией, а здесь всё выражается прямо в терминах базы.
 *
     <p>Строки без {@code sale_price} не попадают ни в одну из сумм: у них {@code commission}
     равен {@code null}, это удержания и штрафы за доставку, и их {@code quantity} равен
     единице. Включение дало бы фиктивные продажи — на реальных данных сентября 2026 это
     119 единиц против 555 настоящих.
     *
     <p>Знак количества у возврата сохраняется положительным: OZON присылает
     {@code quantity = 1} и для возврата, уменьшение несут деньги.
     */
    public Map<Long, Quantities> quantities(Long accountId, LocalDate from, LocalDate to) {
        Map<Long, Quantities> result = new LinkedHashMap<>();
        jdbc.query("""
                select p.sku,
                       coalesce(sum(p.quantity) filter (where p.sale_price > 0), 0),
                       coalesce(sum(p.quantity) filter (where p.sale_price < 0), 0)
                from finance_accrual a
                join posting po on po.finance_accrual_id = a.id
                join posting_product p on p.posting_id = po.id
                where a.seller_account_id = ? and a.accrual_date between ? and ?
                  and p.sale_price is not null
                group by p.sku
                """, rs -> {
            // sum() в PostgreSQL возвращает bigint, поэтому приводить через
            // (Integer) нельзя — будет ClassCastException на Long.
            result.merge(rs.getLong("sku"),
                    new Quantities(number(rs.getObject(2)), number(rs.getObject(3))),
                    (a, b) -> new Quantities(a.sold() + b.sold(), a.returned() + b.returned()));
        }, accountId, from, to);
        return result;
    }

    private static int number(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    /** Единиц по SKU: продано и возвращено за период. */
    public record Quantities(int sold, int returned) {
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