package ru.analizer.sync.infrastructure.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * CONTAINER_FEES — начисление по контейнеру. Категория присутствует в актуальной
 * схеме OZON, хотя в первоначальном описании её не было; сохраняется, чтобы не терять
 * данные, иначе сумма начислений разошлась бы с кабинетом.
 */
@Entity
@Table(name = "container_fee")
public class ContainerFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finance_accrual_id", nullable = false)
    private FinanceAccrual financeAccrual;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "RUB";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ContainerFee() {
    }

    public ContainerFee(FinanceAccrual financeAccrual, Integer typeId, BigDecimal amount, String currency) {
        this.financeAccrual = financeAccrual;
        this.typeId = typeId;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.currency = currency == null ? "RUB" : currency;
    }

    public Long getId() {
        return id;
    }

    public FinanceAccrual getFinanceAccrual() {
        return financeAccrual;
    }

    public Integer getTypeId() {
        return typeId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}
