package ru.analizer.sync.infrastructure.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Товар внутри отправления. Один POSTING может содержать несколько товаров.
 *
 * <p>{@code quantity} отсутствует в опубликованной схеме OZON, но реально приходит в ответе
 * {@code /v1/finance/accrual/by-day}. Если поле не пришло, считаем строки единицами товара.
 */
@Entity
@Table(name = "posting_product")
public class PostingProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "posting_id", nullable = false)
    private Posting posting;

    @Column(name = "sku", nullable = false)
    private Long sku;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "seller_price", precision = 19, scale = 4)
    private BigDecimal sellerPrice;

    @Column(name = "sale_price", precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "sale_amount", precision = 19, scale = 4)
    private BigDecimal saleAmount;

    @Column(name = "sale_commission", precision = 19, scale = 4)
    private BigDecimal saleCommission;

    @Column(name = "commission", precision = 19, scale = 4)
    private BigDecimal commission;

    @Column(name = "commission_ratio", length = 64)
    private String commissionRatio;

    @Column(name = "coinvestment", precision = 19, scale = 4)
    private BigDecimal coinvestment;

    @Column(name = "bonus", precision = 19, scale = 4)
    private BigDecimal bonus;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "RUB";

    @OneToMany(mappedBy = "postingProduct", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeliveryService> deliveryServices = new ArrayList<>();

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected PostingProduct() {
    }

    public PostingProduct(Posting posting, Long sku, String currency) {
        this.posting = posting;
        this.sku = sku;
        this.currency = currency == null ? "RUB" : currency;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setCommissionValues(BigDecimal sellerPrice, BigDecimal salePrice, BigDecimal saleAmount,
                                    BigDecimal saleCommission, BigDecimal commission, String commissionRatio,
                                    BigDecimal coinvestment, BigDecimal bonus) {
        this.sellerPrice = sellerPrice;
        this.salePrice = salePrice;
        this.saleAmount = saleAmount;
        this.saleCommission = saleCommission;
        this.commission = commission;
        this.commissionRatio = commissionRatio;
        this.coinvestment = coinvestment;
        this.bonus = bonus;
    }

    public void addDeliveryService(DeliveryService service) {
        service.attachTo(this);
        this.deliveryServices.add(service);
    }

    public Long getId() {
        return id;
    }

    public Posting getPosting() {
        return posting;
    }

    public Long getSku() {
        return sku;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public BigDecimal getSellerPrice() {
        return sellerPrice;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public BigDecimal getSaleAmount() {
        return saleAmount;
    }

    public BigDecimal getSaleCommission() {
        return saleCommission;
    }

    public BigDecimal getCommission() {
        return commission;
    }

    public String getCommissionRatio() {
        return commissionRatio;
    }

    public BigDecimal getCoinvestment() {
        return coinvestment;
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public String getCurrency() {
        return currency;
    }

    public List<DeliveryService> getDeliveryServices() {
        return deliveryServices;
    }
}
