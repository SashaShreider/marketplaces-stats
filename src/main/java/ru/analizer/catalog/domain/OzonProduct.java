package ru.analizer.catalog.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.analizer.account.domain.SellerAccount;

import java.time.Instant;

/**
 * Товар каталога.
 *
 * <p>Хранит и общие поля товара, и характеристики в общем виде
 * ({@link OzonProductAttribute}), и авторов ({@link ProductAuthor}). Последние два
 * вынесены в отдельные таблицы не для красоты, а по двум причинам: у товара их
 * произвольное число, а множество авторов нужно фильтровать по индексу.
 *
 * <p>{@code authorKeys} и {@code authorSurnames} — производные множества для фильтра.
 * Источник истины — строки в {@link ProductAuthor}; эти массивы можно пересчитать
 * в любой момент, ничего не теряя.
 */
@Entity
@Table(name = "ozon_product")
public class OzonProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_account_id", nullable = false)
    private SellerAccount sellerAccount;

    @Column(name = "sku", nullable = false)
    private long sku;

    @Column(name = "ozon_product_id")
    private Long ozonProductId;

    @Column(name = "offer_id", length = 255)
    private String offerId;

    @Column(name = "name", columnDefinition = "text")
    private String name;

    @Column(name = "barcode", length = 64)
    private String barcode;

    @Column(name = "type_id")
    private Long typeId;

    @Column(name = "description_category_id")
    private Long descriptionCategoryId;

    @Column(name = "primary_image", columnDefinition = "text")
    private String primaryImage;

    /** Атрибут 4184. Дублирует строку характеристик ради быстрого показа в отчёте. */
    @Column(name = "isbn", length = 64)
    private String isbn;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "width_mm")
    private Integer widthMm;

    @Column(name = "height_mm")
    private Integer heightMm;

    @Column(name = "depth_mm")
    private Integer depthMm;

    @Column(name = "model_id")
    private Long modelId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_data", nullable = false, columnDefinition = "jsonb")
    private String rawData;

    @Column(name = "first_seen_at", nullable = false, insertable = false, updatable = false)
    private Instant firstSeenAt;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    /**
     * Сведённые имена авторов для фильтра: GIN-индекс по массиву.
     *
     * <p>Указан именно {@code SqlTypes.ARRAY}: без него Hibernate отправил бы в колонку
     * обычную строку, и PostgreSQL ответил бы «column author_keys is of type text[] but
     * expression is of type character varying».
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "author_keys", nullable = false, columnDefinition = "text[]")
    private String[] authorKeys = new String[0];

    /** Фамилии авторов — для широкого фильтра по одной фамилии. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "author_surnames", nullable = false, columnDefinition = "text[]")
    private String[] authorSurnames = new String[0];

    protected OzonProduct() {
    }

    public OzonProduct(SellerAccount sellerAccount, long sku, Instant lastSyncedAt) {
        this.sellerAccount = sellerAccount;
        this.sku = sku;
        this.lastSyncedAt = lastSyncedAt;
    }

    /**
     * Обновляет товар из ответа OZON.
     *
     * <p>Повторная синхронизация обязана обновлять, а не только вставлять: продавец
     * меняет название, цену и характеристики, и каталог должен показывать текущее
     * состояние, иначе фильтр по автору перестал бы работать после правки карточки.
     */
    public void refreshFrom(Long ozonProductId, String offerId, String name, String barcode,
                            Long typeId, Long descriptionCategoryId, String primaryImage,
                            String isbn, Integer weightGrams, Integer widthMm, Integer heightMm,
                            Integer depthMm, Long modelId, String rawData, Instant now) {
        this.ozonProductId = ozonProductId;
        this.offerId = offerId;
        this.name = name;
        this.barcode = barcode;
        this.typeId = typeId;
        this.descriptionCategoryId = descriptionCategoryId;
        this.primaryImage = primaryImage;
        this.isbn = isbn;
        this.weightGrams = weightGrams;
        this.widthMm = widthMm;
        this.heightMm = heightMm;
        this.depthMm = depthMm;
        this.modelId = modelId;
        this.rawData = rawData;
        this.lastSyncedAt = now;
    }

    public void applyAuthors(String[] keys, String[] surnames) {
        this.authorKeys = keys == null ? new String[0] : keys.clone();
        this.authorSurnames = surnames == null ? new String[0] : surnames.clone();
    }

    public Long getId() {
        return id;
    }

    public SellerAccount getSellerAccount() {
        return sellerAccount;
    }

    public long getSku() {
        return sku;
    }

    public Long getOzonProductId() {
        return ozonProductId;
    }

    public String getOfferId() {
        return offerId;
    }

    public String getName() {
        return name;
    }

    public String getBarcode() {
        return barcode;
    }

    public Long getTypeId() {
        return typeId;
    }

    public Long getDescriptionCategoryId() {
        return descriptionCategoryId;
    }

    public String getPrimaryImage() {
        return primaryImage;
    }

    public String getIsbn() {
        return isbn;
    }

    public Integer getWeightGrams() {
        return weightGrams;
    }

    public Integer getWidthMm() {
        return widthMm;
    }

    public Integer getHeightMm() {
        return heightMm;
    }

    public Integer getDepthMm() {
        return depthMm;
    }

    public Long getModelId() {
        return modelId;
    }

    public String getRawData() {
        return rawData;
    }

    public Instant getFirstSeenAt() {
        return firstSeenAt;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String[] getAuthorKeys() {
        return authorKeys;
    }

    public String[] getAuthorSurnames() {
        return authorSurnames;
    }
}