package ru.analizer.sync.infrastructure.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Детализация расхода по товару: тип начисления + сумма.
 */
@Entity
@Table(name = "item_fee_detail")
public class ItemFeeDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_fee_id", nullable = false)
    private ItemFee itemFee;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "RUB";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ItemFeeDetail() {
    }

    public ItemFeeDetail(Integer typeId, BigDecimal amount, String currency) {
        this.typeId = typeId;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.currency = currency == null ? "RUB" : currency;
    }

    void attachTo(ItemFee itemFee) {
        this.itemFee = itemFee;
    }

    public Long getId() {
        return id;
    }

    public ItemFee getItemFee() {
        return itemFee;
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
