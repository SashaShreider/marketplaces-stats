package ru.analizer.integration.ozon;

import ru.analizer.integration.model.ProductAttributeEntry;
import ru.analizer.integration.model.ProductEntry;
import ru.analizer.integration.ozon.dto.catalog.ProductAttributeV4;
import ru.analizer.integration.ozon.dto.catalog.ProductAttributeValueV4;
import ru.analizer.integration.ozon.dto.catalog.ProductInfoV4;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Разбор товара OZON в доменный вид.
 *
 * <p>Класс публичный и используется в том числе тестами: разбор должен быть один и тот же,
 * иначе проверки разбора ничего не значат о рабочем коде.
 *
 * <p>Размеры и вес приводятся к миллиметрам и граммам: в ответе они приходят со своими
 * единицами ({@code dimension_unit}, {@code weight_unit}), и без приведения к общей
 * единице сравнивать товары между собой нельзя. В выгрузке единицы всегда «mm» и «g»,
 * но полагаться на это — значит сломаться на первом же товаре в сантиметрах.
 */
public final class OzonProductMapper {

    private OzonProductMapper() {
    }

    public static ProductEntry toProductEntry(ProductInfoV4 product, String rawJson) {
        List<ProductAttributeEntry> attributes = new ArrayList<>();
        for (ProductAttributeV4 attribute : product.safeAttributes()) {
            if (attribute.id() == null) {
                continue;
            }
            List<ProductAttributeValueV4> values = attribute.safeValues();
            if (values.isEmpty()) {
                // Атрибут объявлен, но значений нет: сохраняем сам факт объявления.
                // Иначе «атрибута нет» и «атрибут есть и пуст» неразличимы, а в выгрузке
                // таких атрибутов 49 из 1841 — из-за них счётки разных идентификаторов
                // расходились бы (69 вместо 89).
                attributes.add(new ProductAttributeEntry(attribute.id(), 0, null, null));
                continue;
            }
            int position = 0;
            for (ProductAttributeValueV4 value : values) {
                attributes.add(new ProductAttributeEntry(
                        attribute.id(), position++, value.value(), value.dictionaryValueId()));
            }
        }

        return new ProductEntry(
                product.sku(),
                product.id(),
                product.offerId(),
                product.name(),
                product.barcode(),
                product.typeId(),
                product.descriptionCategoryId(),
                product.primaryImage(),
                toGrams(product.weight(), product.weightUnit()),
                toMillimetres(product.width(), product.dimensionUnit()),
                toMillimetres(product.height(), product.dimensionUnit()),
                toMillimetres(product.depth(), product.dimensionUnit()),
                product.modelId(),
                attributes,
                rawJson);
    }

    private static Integer toGrams(Integer value, String unit) {
        if (value == null) {
            return null;
        }
        String u = unit == null ? "g" : unit.trim().toLowerCase(Locale.ROOT);
        return switch (u) {
            case "kg" -> value * 1000;
            case "mg" -> Math.round(value / 1000f);
            case "lb" -> Math.round(value * 453.592f);
            case "oz" -> Math.round(value * 28.3495f);
            default -> value;
        };
    }

    private static Integer toMillimetres(Integer value, String unit) {
        if (value == null) {
            return null;
        }
        String u = unit == null ? "mm" : unit.trim().toLowerCase(Locale.ROOT);
        return switch (u) {
            case "cm" -> value * 10;
            case "m" -> value * 1000;
            case "in" -> Math.round(value * 25.4f);
            default -> value;
        };
    }
}