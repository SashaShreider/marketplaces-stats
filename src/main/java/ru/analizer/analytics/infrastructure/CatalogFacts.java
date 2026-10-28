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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * <p>Суммы в подзапросах повторяют формулы {@link FinancialModel} — продажи плюс
     * возвраты плюс программа партнёров, расходы как сумма модулей комиссии, логистики
     * и прочих списаний. Здесь они нужны только чтобы упорядочить строки; числа в
     * ответе по-прежнему считает {@link FinancialModel}, и тест сверяет, что порядок
     * из SQL совпадает с порядком по этим числам.
     *
     * @param filters фильтры по атрибутам; незаданные ничего не ограничивают
     * @param query   подстрока названия, артикула или ISBN; пустая строка — без фильтра
     * @param sort    критерий порядка: {@code INCOME}, {@code NAME}, {@code SKU},
     *                {@code SOLD}, {@code EXPENSES} или {@code GROSS}
     * @param order   направление: {@code asc} или {@code desc}; пустое — как привычнее
     *                для этого критерия
     * @param onlyWithSales оставить только товары, у которых были продажи
     */
    public List<ProductCatalogRow> productsPage(Long accountId, List<ProductAttributeFilter> filters,
                                                String query,
                                                LocalDate from, LocalDate to,
                                                String sort, String order, boolean onlyWithSales,
                                                int offset, int limit) {
        RowMapper<ProductCatalogRow> mapper = (rs, i) -> new ProductCatalogRow(
                rs.getLong("id"),
                rs.getLong("sku"),
                rs.getString("offer_id"),
                rs.getString("name"),
                rs.getString("primary_image"),
                rs.getString("isbn"),
                rs.getLong("type_id"),
                rs.getBoolean("archived"));
        // ORDER BY подставляется по метке, а не склейкой блоков: склейка text block'ов
        // однажды съела перевод строки и дала «order by …offset».
        String sql = """
                with posting_totals as (
                    select p.sku,
                           sum(case when p.sale_price > 0 then p.sale_price else 0 end)
                             + sum(case when p.sale_price < 0 then p.sale_price else 0 end)
                             + sum(coalesce(p.bonus, 0) + coalesce(p.coinvestment, 0)) as income,
                           sum(case when p.sale_price > 0 then p.quantity else 0 end) as sold,
                           sum(coalesce(p.sale_commission, 0)) as commission
                    from finance_accrual a
                    join posting po on po.finance_accrual_id = a.id
                    join posting_product p on p.posting_id = po.id
                    where a.seller_account_id = :account
                      and a.accrual_date between :from and :to
                    group by p.sku
                ),
                logistics_totals as (
                    select p.sku, sum(d.amount) as logistics
                    from finance_accrual a
                    join posting po on po.finance_accrual_id = a.id
                    join posting_product p on p.posting_id = po.id
                    join delivery_service d on d.posting_product_id = p.id
                    where a.seller_account_id = :account
                      and a.accrual_date between :from and :to
                    group by p.sku
                ),
                item_totals as (
                    select f.sku, sum(d.amount) as item_expenses
                    from finance_accrual a
                    join item_fee f on f.finance_accrual_id = a.id
                    join item_fee_detail d on d.item_fee_id = f.id
                    where a.seller_account_id = :account
                      and a.accrual_date between :from and :to
                    group by f.sku
                )
                select pr.id, pr.sku, pr.offer_id, pr.name, pr.primary_image,
                       pr.isbn, pr.type_id, pr.archived
                from ozon_product pr
                left join posting_totals pt on pt.sku = pr.sku
                left join logistics_totals lt on lt.sku = pr.sku
                left join item_totals it on it.sku = pr.sku
                where pr.seller_account_id = :account
                  and (cast(:queryText as text) = ''
                       or lower(pr.name) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.offer_id, '')) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.isbn, '')) like lower('%' || :queryText || '%'))
                /*WITH_SALES*/
                /*FILTERS*/
                /*ORDER_BY*/
                offset :offset limit :limit
                """
                .replace("/*WITH_SALES*/", onlyWithSalesSql(onlyWithSales))
                .replace("/*FILTERS*/", filterSql(filters, "pr"))
                .replace("/*ORDER_BY*/", orderBy(sort, order));

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
     * Расходы товара в SQL: модуль комиссии, модуль логистики и модуль прочих
     * списаний.
     *
     * <p>Формула повторяет {@code FinancialSummary.expenses()}, но нужна только для
     * порядка строк. Числа в ответе считает модель.
     */
    private static final String EXPENSES_SQL =
            "(abs(coalesce(pt.commission, 0))"
            + " + abs(coalesce(lt.logistics, 0))"
            + " + abs(coalesce(it.item_expenses, 0)))";

    /**
     * Предложение {@code ORDER BY} по ключу сортировки.
     *
     * <p>Ключ подставляется только из этого списка, а не из значения запроса: иначе
     * подставил бы его прямо в SQL. Второй элемент — SKU — всегда добавляется, чтобы
     * порядок был воспроизводимым: без него товары с одинаковым доходом или названием
     * приходили бы в произвольном порядке и между страницами «прыгали».
     *
     * <p>Направление по умолчанию разное у разных критериев: по имени и SKU — по
     * возрастанию (так их и читают), по деньгам и количествам — по убыванию (так
     * видно лучших и худших). Явное направление запроса перебивает умолчание.
     */
    private static String orderBy(String sort, String order) {
        String key = sort == null ? "INCOME" : sort.trim().toUpperCase(java.util.Locale.ROOT);
        String column = switch (key) {
            case "NAME" -> "pr.name";
            case "SKU" -> "pr.sku";
            case "SOLD" -> "coalesce(pt.sold, 0)";
            case "EXPENSES" -> EXPENSES_SQL;
            case "GROSS" -> "(coalesce(pt.income, 0) - " + EXPENSES_SQL + ")";
            // Товар без начислений получает доход 0 и уходит в конец, а не пропадает:
            // в каталоге он есть, и молчать о нём значило бы скрыть товар без продаж.
            default -> "coalesce(pt.income, 0)";
        };
        boolean ascending = switch (key) {
            case "NAME", "SKU" -> true;
            default -> false;
        };
        if (order != null) {
            String direction = order.trim().toLowerCase(java.util.Locale.ROOT);
            if ("asc".equals(direction)) {
                ascending = true;
            } else if ("desc".equals(direction)) {
                ascending = false;
            }
        }
        return "order by " + column + (ascending ? " asc" : " desc") + ", pr.sku";
    }

    /**
     * Условие «только товары с продажами».
     *
     * <p>Продажа — это строка операции с положительной ценой продажи; счётчик
     * «товаров с продажами» в отчёте считается по тому же правилу, поэтому цифры
     * отчёта и отбор совпадают.
     */
    private static String onlyWithSalesSql(boolean onlyWithSales) {
        if (!onlyWithSales) {
            return "";
        }
        return """
                
                  and exists (
                      select 1
                      from finance_accrual a2
                      join posting po2 on po2.finance_accrual_id = a2.id
                      join posting_product p2 on p2.posting_id = po2.id
                      where a2.seller_account_id = :account
                        and a2.accrual_date between :from and :to
                        and p2.sku = pr.sku
                        and p2.sale_price > 0)
                """;
    }

    /**
     * Сколько товаров подходит под фильтры — чтобы клиент знал число страниц.
     *
     * <p>Считает то же, что и выборка страницы: с тем же фильтром «с продажами» и с
     * теми же условиями. Иначе клиент знал бы одно число страниц, а получил бы
     * другое.
     */
    public long productsCount(Long accountId, List<ProductAttributeFilter> filters, String query,
                              LocalDate from, LocalDate to, boolean onlyWithSales) {
        Long count = namedJdbc.queryForObject("""
                select count(*) from ozon_product pr
                where pr.seller_account_id = :account
                  and (cast(:queryText as text) = ''
                       or lower(pr.name) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.offer_id, '')) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.isbn, '')) like lower('%' || :queryText || '%'))
                /*WITH_SALES*/
                /*FILTERS*/
                """.replace("/*WITH_SALES*/", onlyWithSalesSql(onlyWithSales))
                        .replace("/*FILTERS*/", filterSql(filters, "pr")),
                filterParams(accountId, filters, query)
                        .addValue("from", from)
                        .addValue("to", to), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * SKU всех товаров, попавших в отбор, — без пагинации.
     *
     * <p>Нужен для итогов под фильтром: {@code productsPage} отдаёт только текущую
     * страницу, а сумма по ней — это 25 товаров, а не весь результат отбора.
     */
    public List<Long> selectionSkus(Long accountId, List<ProductAttributeFilter> filters, String query,
                                    LocalDate from, LocalDate to, boolean onlyWithSales) {
        return namedJdbc.queryForList("""
                select pr.sku from ozon_product pr
                where pr.seller_account_id = :account
                  and (cast(:queryText as text) = ''
                       or lower(pr.name) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.offer_id, '')) like lower('%' || :queryText || '%')
                       or lower(coalesce(pr.isbn, '')) like lower('%' || :queryText || '%'))
                /*WITH_SALES*/
                /*FILTERS*/
                """.replace("/*WITH_SALES*/", onlyWithSalesSql(onlyWithSales))
                        .replace("/*FILTERS*/", filterSql(filters, "pr")),
                filterParams(accountId, filters, query)
                        .addValue("from", from)
                        .addValue("to", to),
                Long.class);
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
     * Товары выбранного автора — одним множеством номеров.
     *
     * <p>Отчёт по автору фильтрует факты по SKU, а не подзапросом в каждом запросе:
     * список товаров автора невелик, и найти его дешевле один раз, чем повторять
     * условие по {@code product_author} в четырёх выборках фактов.
     *
     * <p>Ищется точное совпадение, как и в {@code ProductAuthorFilter}: «Сурцуков А.»
     * не найдёт «Сурцуков Анатолий». Пользователь выбирает автора из подсказки, где
     * лежат ровно те строки, что есть в базе.
     */
    public Set<Long> skusByAuthor(Long accountId, String author) {
        if (author == null || author.isBlank()) {
            return Set.of();
        }
        return new LinkedHashSet<>(jdbc.query("""
                select distinct sku
                from product_author
                where seller_account_id = ? and author_raw = ?
                """, (rs, i) -> rs.getLong(1), accountId, author));
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

/**
 * Динамика продаж по дням периода: сколько единиц продано и возвращено.
 *
 * <p>Считается по тем же правилам, что и количество в отчёте по товарам:
 * {@code sale_price > 0} — продажа, {@code < 0} — возврат, а строки без цены
 * (удержания и штрафы за доставку) не попадают ни туда, ни сюда. Разные правила в
 * двух местах дали бы расхождение: итог отчёта и сумма под графиком сошлись бы
 * только случайно.
 *
 * <p>Каталог присоединяется <b>слева</b>, а не внутри. Товара может не быть в
 * каталоге — он снят с продажи или карточка ещё не загружена, — но его продажи
 * от этого не перестают быть продажами, и сумма под графиком обязана сходиться с
 * итогами. При фильтре по автору ненужные товары отсекает сам фильтр: он ищет
 * строку в {@code product_author}, которой у товара без карточки просто нет.
 *
 * @param filters отбор по атрибутам товара; используются те же, что и в отчёте
 * @param sku     конкретный SKU; {@code null} — без ограничения по SKU
 */
public List<SalesDay> salesDynamics(Long accountId, List<ProductAttributeFilter> filters,
                                     Long sku, LocalDate from, LocalDate to) {
    MapSqlParameterSource params = filterParams(accountId, filters, null)
            .addValue("from", from)
            .addValue("to", to);
    String skuClause = "";
    if (sku != null) {
        skuClause = "\n  and p.sku = :sku";
        params.addValue("sku", sku);
    }
    // sum() в PostgreSQL возвращает bigint, поэтому приводить через (Integer)
    // нельзя — будет ClassCastException. Как и в quantities().
    RowMapper<SalesDay> mapper = (rs, i) -> new SalesDay(rs.getDate(1).toLocalDate(),
            number(rs.getObject(2)), number(rs.getObject(3)));
    return namedJdbc.query("""
            select a.accrual_date,
                   coalesce(sum(p.quantity) filter (where p.sale_price > 0), 0) as sold,
                   coalesce(sum(p.quantity) filter (where p.sale_price < 0), 0) as returned
            from finance_accrual a
            join posting po on po.finance_accrual_id = a.id
            join posting_product p on p.posting_id = po.id
            left join ozon_product pr
                   on pr.seller_account_id = a.seller_account_id and pr.sku = p.sku
            where a.seller_account_id = :account
              and a.accrual_date between :from and :to
              and p.sale_price is not null
            /*FILTERS*/
            /*SKU*/
            group by a.accrual_date
            order by a.accrual_date
            """.replace("/*FILTERS*/", filterSql(filters, "pr")).replace("/*SKU*/", skuClause),
            params,
            mapper);
}

/**
 * Продажи и возвраты за один день.
 *
 * @param date     день
 * @param sold     продано единиц
 * @param returned возвращено единиц
 */
public record SalesDay(LocalDate date, int sold, int returned) {
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
  Long typeId,
  /**
   * Товар снят с продажи и убран в архив OZON.
   *
   * <p>Показывается в отчёте наравне с обычными: начисления по нему есть, и его
   * расходы — часть общей картины. Отличать надо только видимым признаком, чтобы
   * продавец не искал в своём кабинете то, что оттуда уже убрано.
   */
  boolean archived
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