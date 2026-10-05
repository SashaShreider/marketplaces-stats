package ru.analizer.sync.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Отправление внутри операции категории POSTING. Связь 1:1 с {@link FinanceAccrual}.
 */
@Entity
@Table(name = "posting")
public class Posting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finance_accrual_id", nullable = false, unique = true)
    private FinanceAccrual financeAccrual;

    @Column(name = "delivery_schema", length = 32)
    private String deliverySchema;

    @Column(name = "delivery_speed")
    private Integer deliverySpeed;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Posting() {
    }

    public Posting(FinanceAccrual financeAccrual, String deliverySchema, Integer deliverySpeed) {
        this.financeAccrual = financeAccrual;
        this.deliverySchema = deliverySchema;
        this.deliverySpeed = deliverySpeed;
    }

    public Long getId() {
        return id;
    }

    public FinanceAccrual getFinanceAccrual() {
        return financeAccrual;
    }

    public String getDeliverySchema() {
        return deliverySchema;
    }

    public Integer getDeliverySpeed() {
        return deliverySpeed;
    }
}
