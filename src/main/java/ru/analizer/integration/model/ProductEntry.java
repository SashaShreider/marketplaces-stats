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
     * <p>Годится для признаков, где значение одно: ISBN, тип товара. Для перечислений
     * вроде автора нужен {@link #attributeValues(long)} — иначе молча теряются все
     * значения, кроме первого.
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

    /**
     * Все непустые значения атрибута в том порядке, в каком их вернул OZON.
     *
     * <p>Нужно там, где атрибут перечисляет несколько сущностей. Реальный пример —
     * «Автор на обложке» (105): у части товаров OZON присылает не одну строку, а
     * массив, где каждый автор отдельным элементом. Взять отсюда первое значение —
     * значит потерять остальных молча: товар покажется с одним автором вместо трёх,
     * и отчёт по продажам этого продавца разойдётся с его же карточкой.
     *
     * @return значения в порядке появления; пустой список, если атрибута нет
     */
    public List<String> attributeValues(long attributeId) {
        return attributes.stream()
                .filter(a -> a.attributeId() == attributeId)
                .map(ProductAttributeEntry::value)
                .filter(v -> v != null && !v.isBlank())
                .toList();
    }
}