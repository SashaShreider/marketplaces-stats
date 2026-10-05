package ru.analizer.marketplace;

/**
 * Значение характеристики товара.
 *
 * @param position порядок значения внутри атрибута; у одного атрибута в текущей выгрузке
 *                 значение всегда одно, но массив значений по спецификации возможен,
 *                 и порядок в нём значим
 */
public record ProductAttributeEntry(
        long attributeId,
        int position,
        String value,
        Long dictionaryValueId
) {
}