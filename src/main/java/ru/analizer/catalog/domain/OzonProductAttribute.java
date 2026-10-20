package ru.analizer.catalog.domain;

import jakarta.persistence.*;
import lombok.Getter;

/**
 * Значение характеристики товара.
 *
 * <p>Отдельная строка на каждое значение: у товара их 18, и состав меняется при смене
 * категории. Общий вид хранения выбран сознательно — сегодня нужны три атрибута
 * (автор, автор на обложке, ISBN), завтра понадобится четвёртый, и перезагружать
 * каталог ради него не захочется.
 */
@Entity
@Getter
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
}
