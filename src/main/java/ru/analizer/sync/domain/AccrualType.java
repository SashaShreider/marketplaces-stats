package ru.analizer.sync.domain;

import jakarta.persistence.*;
import ru.analizer.account.domain.Marketplace;

import java.time.Instant;

/**
 * Справочник типов начислений. Заполняется из /v1/finance/accrual/types, поэтому
 * бизнес-логика не должна опираться на захардкоженный список type_id.
 */
@Entity
@Table(name = "accrual_type")
public class AccrualType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marketplace_id", nullable = false)
    private Marketplace marketplace;

    @Column(name = "external_type_id", nullable = false)
    private Integer externalTypeId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected AccrualType() {
    }

    public AccrualType(Marketplace marketplace, Integer externalTypeId, String name, String description) {
        this.marketplace = marketplace;
        this.externalTypeId = externalTypeId;
        this.name = name;
        this.description = description;
    }

    public void updateFrom(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public Integer getExternalTypeId() {
        return externalTypeId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
