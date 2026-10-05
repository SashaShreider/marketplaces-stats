package ru.analizer.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Значение характеристики товара.
 *
 * <p>Отдельная строка на каждое значение: у товара их 18, и состав меняется при смене
 * категории. Общий вид хранения выбран сознательно — сегодня нужны четыре атрибута,
 * завтра понадобится пятый, и перезагружать каталог ради него не захочется.
 */
@Entity
@Table(name = "ozon_product_attribute")
public class OzonProductAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_account_id", nullable = false)
    private Long sellerAccountId;

    @Column(name = "sku", nullable = false)
    private long sku;

    @Column(name = "attribute_id", nullable = false)
    private long attributeId;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "value", columnDefinition = "text")
    private String value;

    @Column(name = "dictionary_value_id")
    private Long dictionaryValueId;

    protected OzonProductAttribute() {
    }

    public OzonProductAttribute(Long sellerAccountId, long sku, long attributeId,
                                int position, String value, Long dictionaryValueId) {
        this.sellerAccountId = sellerAccountId;
        this.sku = sku;
        this.attributeId = attributeId;
        this.position = position;
        this.value = value;
        this.dictionaryValueId = dictionaryValueId;
    }

    public void updateValue(String value, Long dictionaryValueId) {
        this.value = value;
        this.dictionaryValueId = dictionaryValueId;
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

    public long getAttributeId() {
        return attributeId;
    }

    public int getPosition() {
        return position;
    }

    public String getValue() {
        return value;
    }

    public Long getDictionaryValueId() {
        return dictionaryValueId;
    }
}