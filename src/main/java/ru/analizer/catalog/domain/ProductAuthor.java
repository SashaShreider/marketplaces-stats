package ru.analizer.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Автор товара — одна строка на одного автора в одном поле.
 *
 * <p>{@code authorRaw} хранит значение ровно как вернул OZON. Это источник истины и то,
 * что показывается пользователю: формат имён у OZON скачет, и любое сведение рано или
 * поздно станет причиной спора «это один автор или два».
 */
@Entity
@Table(name = "product_author")
public class ProductAuthor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_account_id", nullable = false)
    private Long sellerAccountId;

    @Column(name = "sku", nullable = false)
    private long sku;

    @Column(name = "author_raw", nullable = false, columnDefinition = "text")
    private String authorRaw;

    /** DECLARED — атрибут 4182 «Автор»; COVER — 105 «Автор на обложке». */
    @Column(name = "source", nullable = false, length = 16)
    private String source;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "position", nullable = false)
    private int position;

    protected ProductAuthor() {
    }

    public ProductAuthor(Long sellerAccountId, long sku, String authorRaw,
                         String source, boolean primary, int position) {
        this.sellerAccountId = sellerAccountId;
        this.sku = sku;
        this.authorRaw = authorRaw;
        this.source = source;
        this.primary = primary;
        this.position = position;
    }

    public Long getId() {
        return id;
    }

    public Long getSellerAccountId() {
        return sellerAccountId;
    }

    public long getSku() {
        return sku;
    }

    public String getAuthorRaw() {
        return authorRaw;
    }

    public String getSource() {
        return source;
    }

    public boolean isPrimary() {
        return primary;
    }

    public int getPosition() {
        return position;
    }
}