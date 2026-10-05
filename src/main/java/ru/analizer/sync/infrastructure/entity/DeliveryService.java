package ru.analizer.sync.infrastructure.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Услуга доставки внутри товара. Значения отрицательные — это расход.
 * Конкретные {@code type_id} в бизнес-логику не зашиваются.
 */
@Entity
@Table(name = "delivery_service")
public class DeliveryService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posting_product_id", nullable = false)
    private PostingProduct postingProduct;

    @Column(name = "type_id", nullable = false)
    private Integer typeId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "RUB";

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected DeliveryService() {
    }

    public DeliveryService(Integer typeId, BigDecimal amount, String currency) {
        this.typeId = typeId;
        this.amount = amount == null ? BigDecimal.ZERO : amount;
        this.currency = currency == null ? "RUB" : currency;
    }

    void attachTo(PostingProduct postingProduct) {
        this.postingProduct = postingProduct;
    }

    public Long getId() {
        return id;
    }

    public PostingProduct getPostingProduct() {
        return postingProduct;
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
