package ru.analizer.analytics;

import jakarta.persistence.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Выборка фактов для аналитики.
 *
 * <p>Считаем в SQL только то, что не требует разбора: агрегаты по датам и счётчики.
 * Всё, где нужна интерпретация знака (продажа это или возврат), делается
 * в {@link FinancialModel} — чтобы правила не расползлись по запросам.
 */
@Repository
public class AnalyticsFactsRepository {

    private final JdbcTemplate jdbc;

    public AnalyticsFactsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Сумма всех операций за день — это и есть «к выплате» по данным OZON. */
    public Map<LocalDate, BigDecimal> payoutsByDate(Long sellerAccountId, LocalDate from, LocalDate to) {
        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        jdbc.query("""
                select accrual_date, sum(total_amount)
                from finance_accrual
                where seller_account_id = ? and accrual_date between ? and ?
                group by accrual_date
                order by accrual_date
                """, rs -> {
            result.put(rs.getObject("accrual_date", LocalDate.class), rs.getBigDecimal(2));
        }, sellerAccountId, from, to);
        return result;
    }

    /** Товары внутри операций POSTING. */
    public List<ProductFact> products(Long sellerAccountId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                select a.accrual_date, a.unit_number, a.external_id, p.sku, p.quantity,
                       p.sale_price, p.sale_commission, p.bonus, p.coinvestment
                from finance_accrual a
                join posting po on po.finance_accrual_id = a.id
                join posting_product p on p.posting_id = po.id
                where a.seller_account_id = ? and a.accrual_date between ? and ?
                order by a.accrual_date, a.external_id, p.sku
                """, (rs, i) -> new ProductFact(
                        rs.getObject("accrual_date", LocalDate.class),
                        rs.getString("unit_number"),
                        rs.getLong("external_id"),
                        rs.getLong("sku"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("sale_price"),
                        rs.getBigDecimal("sale_commission"),
                        rs.getBigDecimal("bonus"),
                        rs.getBigDecimal("coinvestment")),
                sellerAccountId, from, to);
    }

    /**
     * Логистика: каждая услуга доставки отдельной строкой.
     * SKU известен, поэтому расход можно привязать к товару.
     */
    public List<FeeFact> deliveryFees(Long sellerAccountId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                select a.accrual_date, a.unit_number, a.external_id, p.sku, d.type_id, d.amount
                from finance_accrual a
                join posting po on po.finance_accrual_id = a.id
                join posting_product p on p.posting_id = po.id
                join delivery_service d on d.posting_product_id = p.id
                where a.seller_account_id = ? and a.accrual_date between ? and ?
                """, (rs, i) -> new FeeFact(
                        rs.getObject("accrual_date", LocalDate.class),
                        rs.getString("unit_number"),
                        rs.getLong("external_id"),
                        rs.getLong("sku"),
                        (Integer) rs.getObject("type_id"),
                        rs.getBigDecimal("amount"),
                        FeeFact.FeeKind.DELIVERY),
                sellerAccountId, from, to);
    }

    /** ITEM: расход, привязанный к SKU. */
    public List<FeeFact> itemFees(Long sellerAccountId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                select a.accrual_date, a.unit_number, a.external_id, f.sku, d.type_id, d.amount
                from finance_accrual a
                join item_fee f on f.finance_accrual_id = a.id
                join item_fee_detail d on d.item_fee_id = f.id
                where a.seller_account_id = ? and a.accrual_date between ? and ?
                """, (rs, i) -> new FeeFact(
                        rs.getObject("accrual_date", LocalDate.class),
                        rs.getString("unit_number"),
                        rs.getLong("external_id"),
                        rs.getLong("sku"),
                        (Integer) rs.getObject("type_id"),
                        rs.getBigDecimal("amount"),
                        FeeFact.FeeKind.ITEM),
                sellerAccountId, from, to);
    }

    /** NON_ITEM: расход без привязки к SKU. Такие расходы не распределяются. */
    public List<FeeFact> nonItemFees(Long sellerAccountId, LocalDate from, LocalDate to) {
        return jdbc.query("""
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
                        FeeFact.FeeKind.NON_ITEM),
                sellerAccountId, from, to);
    }

    /** CONTAINER_FEES: начисления по контейнеру. */
    public List<FeeFact> containerFees(Long sellerAccountId, LocalDate from, LocalDate to) {
        return jdbc.query("""
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
                        FeeFact.FeeKind.CONTAINER),
                sellerAccountId, from, to);
    }

    /** Названия типов начислений для расшифровки расходов. */
    public Map<Integer, String> accrualTypeNames() {
        Map<Integer, String> names = new LinkedHashMap<>();
        jdbc.query("""
                select external_type_id, name from accrual_type
                """, rs -> {
            names.put(rs.getInt(1), rs.getString(2));
        });
        return names;
    }

    /** Дни, за которые в базе есть операции, — чтобы отчёт не пропускал пустые дни молча. */
    public List<LocalDate> datesWithAccruals(Long sellerAccountId, LocalDate from, LocalDate to) {
        List<LocalDate> dates = new ArrayList<>();
        jdbc.query("""
                select distinct accrual_date from finance_accrual
                where seller_account_id = ? and accrual_date between ? and ?
                order by accrual_date
                """, rs -> {
            dates.add(rs.getObject(1, LocalDate.class));
        }, sellerAccountId, from, to);
        return dates;
    }
}
