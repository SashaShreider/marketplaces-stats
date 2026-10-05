package ru.analizer.sync.infrastructure.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Расходы одной ITEM-операции, привязанные к конкретному SKU.
 * Один ITEM может содержать несколько SKU — это отдельные строки {@code item_fee}.
 */
@Entity
@Table(name = "item_fee")
public class ItemFee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finance_accrual_id", nullable = false)
    private FinanceAccrual financeAccrual;

    @Column(name = "sku", nullable = false)
    private Long sku;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @OneToMany(mappedBy = "itemFee", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ItemFeeDetail> details = new ArrayList<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected ItemFee() {
    }

    public ItemFee(FinanceAccrual financeAccrual, Long sku) {
        this.financeAccrual = financeAccrual;
        this.sku = sku;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void addDetail(ItemFeeDetail detail) {
        detail.attachTo(this);
        this.details.add(detail);
    }

    public Long getId() {
        return id;
    }

    public FinanceAccrual getFinanceAccrual() {
        return financeAccrual;
    }

    public Long getSku() {
        return sku;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public List<ItemFeeDetail> getDetails() {
        return details;
    }
}
