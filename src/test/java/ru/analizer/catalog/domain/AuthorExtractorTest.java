package ru.analizer.catalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import ru.analizer.catalog.domain.AuthorExtractor.Author;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Разбор значения атрибута «Автор» на отдельных авторов.
 *
 * <p>Случаи взяты из настоящей выгрузки {@code /v4/product/info/attributes}:
 * 99 товаров с атрибутом 4182 и 23 с атрибутом 105, всего 108 товаров. Формат имён
 * у продавца скачет, и разбор обязан это выдерживать, не теряя и не склеивая.
 */
class AuthorExtractorTest {

    @Nested
    @DisplayName("4182 «Автор»: несколько авторов в одной строке через запятую")
    class DeclaredAttribute {

        @Test
        @DisplayName("Запятая разделяет: два автора становятся двумя строками")
        void commaSeparatesAuthors() {
            List<Author> authors = AuthorExtractor.extract(List.of("Черняев А. Ю., Ланде А. А."), true);

            assertThat(authors).extracting(Author::raw)
                    .containsExactly("Черняев А. Ю.", "Ланде А. А.");
            assertThat(authors).extracting(Author::position)
                    .containsExactly(0, 1);
        }

        @Test
        @DisplayName("Трёх авторов через запятую разбираются все")
        void threeAuthors() {
            List<Author> authors = AuthorExtractor.extract(
                    List.of("Б. И. Жильцов, В. К. Марков, В. С. Федосеев"), true);

            assertThat(authors).extracting(Author::raw)
                    .containsExactly("Б. И. Жильцов", "В. К. Марков", "В. С. Федосеев");
        }

        @Test
        @DisplayName("Двойная фамилия без разделителя остаётся одним автором")
        void doubleSurnameIsNotSplit() {
            List<Author> authors = AuthorExtractor.extract(List.of("Умнова-Конюхова И.А."), true);

            assertThat(authors).extracting(Author::raw).containsExactly("Умнова-Конюхова И.А.");
        }

        @Test
        @DisplayName("Хвостовой пробел убирается: в выгрузке он есть у «Кондрашева Л. И. »")
        void trailingSpaceTrimmed() {
            List<Author> authors = AuthorExtractor.extract(List.of("Кондрашева Л. И. "), true);

            // Пробел на конце строки иначе попал бы и в базу, и в значение фильтра:
            // подсказка прислала бы «Кондрашева Л. И.», а сравнение не сошлось бы.
            assertThat(authors).extracting(Author::raw).containsExactly("Кондрашева Л. И.");
        }
    }

    @Nested
    @DisplayName("105 «Автор на обложке»: авторы приходят отдельными значениями массива")
    class CoverAttribute {

        @Test
        @DisplayName("Три значения массива дают трёх авторов, а не теряются")
        void arrayValuesBecomeThreeAuthors() {
            List<Author> authors = AuthorExtractor.extract(List.of(
                    "Умнова Ирина Анатольевна",
                    "Конюхова Ирина Анатольевна",
                    "Умнова-Конюхова Ирина Анатольевна"), true);

            assertThat(authors).extracting(Author::raw).containsExactly(
                    "Умнова Ирина Анатольевна",
                    "Конюхова Ирина Анатольевна",
                    "Умнова-Конюхова Ирина Анатольевна");
        }

        @Test
        @DisplayName("Одно значение массива, в свою очередь, делится по запятой")
        void arrayValueIsAlsoSplitByComma() {
            // На случай, если OZON начнёт отдавать оба вида в одной выгрузке:
            // правило одно, а не два.
            List<Author> authors = AuthorExtractor.extract(
                    List.of("Горячев Александр, Плынов Дмитрий"), true);

            assertThat(authors).extracting(Author::raw)
                    .containsExactly("Горячев Александр", "Плынов Дмитрий");
        }

        @Test
        @DisplayName("Однофамильные значения не склеиваются: это три строки карточки")
        void sameSurnameIsNotMerged() {
            // Один и тот же человек описан в карточке тремя разными фамилиями. Склеивать
            // их автоматикой нельзя — значило бы стереть у продавца его же данные.
            List<Author> authors = AuthorExtractor.extract(List.of(
                    "Акимова Татьяна", "Акимова Татьяна Викторовна"), true);

            assertThat(authors).hasSize(2);
        }
    }

    @Nested
    @DisplayName("Контракт «как написано»: ничего не сокращаем и не приводим")
    class StrictContract {

        @Test
        @DisplayName("Разные написания одного человека остаются разными авторами")
        void differentSpellingsStayDifferent() {
            // Четыре написания Сурцукова из выгрузки. Раньше сведение склеивало их в один
            // ключ «сурцуков а.», и фильтр по фамилии находил товары всех. Теперь
            // склейки нет: ищется ровно то, что введено.
            List<Author> authors = AuthorExtractor.extract(List.of(
                    "Сурцуков А.",
                    "Сурцуков Анатолий",
                    "А.В. Сурцуков",
                    "Анатолий Васильевич Сурцуков"), true);

            assertThat(authors).extracting(Author::raw).containsExactly(
                    "Сурцуков А.",
                    "Сурцуков Анатолий",
                    "А.В. Сурцуков",
                    "Анатолий Васильевич Сурцуков");
        }

        @Test
        @DisplayName("Регистр и порядок слов сохраняются как в источнике")
        void caseAndWordOrderPreserved() {
            List<Author> authors = AuthorExtractor.extract(List.of("асилий Калязин"), true);

            assertThat(authors).extracting(Author::raw).containsExactly("асилий Калязин");
        }

        @Test
        @DisplayName("Слово «и» разделителем не считается")
        void conjunctionIsNotSeparator() {
            // «Галина и Павел Барышниковы» — одно значение атрибута, значит один автор.
            List<Author> authors = AuthorExtractor.extract(List.of("Галина и Павел Барышниковы"), true);

            assertThat(authors).extracting(Author::raw)
                    .containsExactly("Галина и Павел Барышниковы");
        }

        @Test
        @DisplayName("Повтор одного и того же имени схлопывается")
        void duplicateIsRemoved() {
            List<Author> authors = AuthorExtractor.extract(List.of(
                    "Сурцуков Анатолий", "Сурцуков Анатолий"), true);

            assertThat(authors).extracting(Author::raw).containsExactly("Сурцуков Анатолий");
        }
    }

    @Nested
    @DisplayName("Пустые значения")
    class EmptyValues {

        @Test
        @DisplayName("Из пустого значения не получается ни одного автора")
        void blankValueYieldsNothing() {
            assertThat(AuthorExtractor.extract(List.of(""), true)).isEmpty();
            assertThat(AuthorExtractor.extract(List.of("   "), true)).isEmpty();
            // List.of(null) запрещён, поэтому список с null составляем вручную — таким
            // значением атрибут может прийти от OZON, и разбор обязан его пережить.
            assertThat(AuthorExtractor.extract(java.util.Arrays.asList((String) null), true)).isEmpty();
            assertThat(AuthorExtractor.extract(null, true)).isEmpty();
            assertThat(AuthorExtractor.extract(List.of(), true)).isEmpty();
        }

        @Test
        @DisplayName("Пустые значения не мешают разбору остальных")
        void blanksDoNotBreakParsing() {
            List<Author> authors = AuthorExtractor.extract(
                    List.of("", "Черняев А. Ю., Ланде А. А.", "  "), true);

            assertThat(authors).extracting(Author::raw)
                    .containsExactly("Черняев А. Ю.", "Ланде А. А.");
        }
    }
}