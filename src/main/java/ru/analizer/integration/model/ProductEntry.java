package ru.analizer.integration.model;

import java.util.List;

/**
 * Товар каталога в виде, в котором его удобно сохранять и показывать.
 *
 * @param sku     идентификатор товара в OZON; связывает каталог с начислениями
 * @param rawJson исходный JSON товара целиком — чтобы ничто не потерялось при смене правил
 */
public record ProductEntry(
        long sku,
        Long ozonProductId,
        String offerId,
        String name,
        String barcode,
        Long typeId,
        Long descriptionCategoryId,
        String primaryImage,
        Integer weightGrams,
        Integer widthMm,
        Integer heightMm,
        Integer depthMm,
        Long modelId,
        List<ProductAttributeEntry> attributes,
        String rawJson
) {

    public ProductEntry {
        attributes = attributes == null ? List.of() : List.copyOf(attributes);
    }

    /**
     * Первое непустое значение атрибута.
     *
     * @return значение или {@code null}, если такого атрибута нет
     */
    public String attributeValue(long attributeId) {
        return attributes.stream()
                .filter(a -> a.attributeId() == attributeId)
                .map(ProductAttributeEntry::value)
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse(null);
    }
}