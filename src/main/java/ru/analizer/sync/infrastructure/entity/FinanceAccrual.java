package ru.analizer.sync.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import ru.analizer.account.domain.SellerAccount;
import ru.analizer.sync.domain.AccrualType;

/**
 * Одна финансовая операция OZON.
 *
 * <p>Уникальный ключ — {@code (seller_account_id, external_id)}, где {@code external_id}
 * это {@code accrual_id} из ответа API. {@code unitNumber} уникальным не является:
 * на одно отправление приходится несколько операций (продажа, комиссия, логистика и т.д.).
 */
@Entity
@Table(name = "finance_accrual")
public class FinanceAccrual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_account_id", nullable = false)
    private SellerAccount sellerAccount;

    @Column(name = "external_id", nullable = false)
    private Long externalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accrual_type_id")
    private AccrualType accrualType;

    @Column(name = "accrual_date", nullable = false)
    private LocalDate accrualDate;

    @Column(name = "unit_number")
    private String unitNumber;

    @Column(name = "accrued_category", nullable = false, length = 32)
    private String accruedCategory;

    @Column(name = "ozon_type_id")
    private Integer ozonTypeId;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "RUB";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", nullable = false, columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected FinanceAccrual() {
    }

    public FinanceAccrual(SellerAccount sellerAccount, Long externalId, LocalDate accrualDate,
                           String unitNumber, String accruedCategory, Integer ozonTypeId,
                           BigDecimal totalAmount, String currency, String rawData) {
        this.sellerAccount = sellerAccount;
        this.externalId = externalId;
        this.accrualDate = accrualDate;
        this.unitNumber = unitNumber;
        this.accruedCategory = accruedCategory;
        this.ozonTypeId = ozonTypeId;
        this.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        this.currency = currency == null ? "RUB" : currency;
        this.rawData = rawData;
    }

    /**
     * OZON уточняет начисления после первой выгрузки, поэтому существующую операцию
     * обновляем, а не заменяем — иначе потеряем неизвестные нам поля.
     */
    public void refreshFrom(LocalDate accrualDate, String unitNumber, String accruedCategory,
                            Integer ozonTypeId, BigDecimal totalAmount, String currency, String rawData) {
        this.accrualDate = accrualDate;
        this.unitNumber = unitNumber;
        this.accruedCategory = accruedCategory;
        this.ozonTypeId = ozonTypeId;
        this.totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        this.currency = currency == null ? "RUB" : currency;
        this.rawData = rawData;
    }

    public void linkAccrualType(AccrualType accrualType) {
        this.accrualType = accrualType;
    }

    public Long getId() {
        return id;
    }

    public SellerAccount getSellerAccount() {
        return sellerAccount;
    }

    public Long getExternalId() {
        return externalId;
    }

    public AccrualType getAccrualType() {
        return accrualType;
    }

    public LocalDate getAccrualDate() {
        return accrualDate;
    }

    public String getUnitNumber() {
        return unitNumber;
    }

    public String getAccruedCategory() {
        return accruedCategory;
    }

    public Integer getOzonTypeId() {
        return ozonTypeId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getRawData() {
        return rawData;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
