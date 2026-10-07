package ru.analizer.integration.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Доступ к значениям характеристик товара.
 *
 * <p>Проверяется в первую очередь различие между {@code attributeValue} и
 * {@code attributeValues}: у перечислений вроде автора OZON присылает массив, и
 * взятие одного значения теряет остальных молча — товар показывается с одним
 * автором вместо трёх, и отчёт о продажах разходится с карточкой.
 */
class ProductEntryTest {

    private static final long AUTHOR = 4182L;
    private static final long COVER_AUTHOR = 105L;
    private static final long ISBN = 4184L;

    private static ProductEntry entry(ProductAttributeEntry... attributes) {
        return new ProductEntry(1L, null, "OFFER-1", "Книга", null, null, null, null,
                null, null, null, null, null, List.of(attributes), null);
    }

    @Nested
    @DisplayName("Несколько значений одного атрибута")
    class SeveralValues {

        @Test
        @DisplayName("Возвращаются все значения в порядке появления")
        void returnsAllValues() {
            // Реальный случай из выгрузки: «Автор на обложке» приходит тремя
            // элементами массива — это три автора одной карточки.
            ProductEntry product = entry(
                    new ProductAttributeEntry(COVER_AUTHOR, 0, "Умнова Ирина Анатольевна", null),
                    new ProductAttributeEntry(COVER_AUTHOR, 1, "Конюхова Ирина Анатольевна", null),
                    new ProductAttributeEntry(COVER_AUTHOR, 2, "Умнова-Конюхова Ирина Анатольевна", null));

            assertThat(product.attributeValues(COVER_AUTHOR)).containsExactly(
                    "Умнова Ирина Анатольевна",
                    "Конюхова Ирина Анатольевна",
                    "Умнова-Конюхова Ирина Анатольевна");
        }

        @Test
        @DisplayName("Значения разных атрибутов не смешиваются")
        void doesNotMixAttributes() {
            ProductEntry product = entry(
                    new ProductAttributeEntry(AUTHOR, 0, "Сурцуков А.", null),
                    new ProductAttributeEntry(COVER_AUTHOR, 0, "Сурцуков Анатолий", null),
                    new ProductAttributeEntry(ISBN, 0, "9785907081338", null));

            assertThat(product.attributeValues(AUTHOR)).containsExactly("Сурцуков А.");
            assertThat(product.attributeValues(COVER_AUTHOR)).containsExactly("Сурцуков Анатолий");
            assertThat(product.attributeValues(ISBN)).containsExactly("9785907081338");
        }

        @Test
        @DisplayName("Пустые значения отбрасываются, а не обрывают список")
        void blankValuesSkipped() {
            // OZON присылает и значения, и пустые строки между ними. Пустая строка —
            // не автор, и обрывать из-за неё список нельзя.
            ProductEntry product = entry(
                    new ProductAttributeEntry(COVER_AUTHOR, 0, "Умнова Ирина", null),
                    new ProductAttributeEntry(COVER_AUTHOR, 1, null, null),
                    new ProductAttributeEntry(COVER_AUTHOR, 2, "   ", null),
                    new ProductAttributeEntry(COVER_AUTHOR, 3, "Конюхова Ирина", null));

            assertThat(product.attributeValues(COVER_AUTHOR))
                    .containsExactly("Умнова Ирина", "Конюхова Ирина");
        }
    }

    @Nested
    @DisplayName("Пустое и отсутствующее")
    class Missing {

        @Test
        @DisplayName("Нет атрибута — пустой список, а не null")
        void absentAttributeGivesEmptyList() {
            // Импорт разбирает результат без проверки на null: null здесь означал бы
            // падение там, где у товара просто нет автора.
            assertThat(entry().attributeValues(AUTHOR)).isEmpty();
            assertThat(entry().attributeValue(AUTHOR)).isNull();
        }

        @Test
        @DisplayName("Одно значение — список из одного элемента")
        void singleValueGivesSingleElementList() {
            ProductEntry product = entry(new ProductAttributeEntry(ISBN, 0, "9785907081338", null));

            assertThat(product.attributeValues(ISBN)).containsExactly("9785907081338");
        }
    }
}