package ru.analizer.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Подключённый аккаунт продавца. API-ключ намеренно не хранится в БД:
 * он берётся из переменной окружения, чтобы секрет не покидал контур конфигурации.
 */
@Entity
@Table(name = "seller_account")
public class SellerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "marketplace_id", nullable = false)
    private Marketplace marketplace;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected SellerAccount() {
    }

    public SellerAccount(Marketplace marketplace, String name, String clientId) {
        this.marketplace = marketplace;
        this.name = name;
        this.clientId = clientId;
    }

    public Long getId() {
        return id;
    }

    public Marketplace getMarketplace() {
        return marketplace;
    }

    public String getName() {
        return name;
    }

    public String getClientId() {
        return clientId;
    }
}
