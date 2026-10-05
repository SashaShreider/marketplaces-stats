package ru.analizer.catalog.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Разбор и сведение авторов.
 *
 * <p>Проверяется на значениях из настоящей выгрузки {@code /v4/product/info/attributes}:
 * формат имён у OZON скачет, и именно на этом ломаются фильтры.
 */
class AuthorNormalizerTest {

    @Test
    @DisplayName("«Фамилия Имя Отчество» сводится к «Фамилия И. О.»")
    void fullNameBecomesSurnameAndInitials() {
        assertThat(AuthorNormalizer.toKey("Шибанов Георгий Петрович"))
                .isEqualTo("шибанов г. п.");
    }

    @Test
    @DisplayName("«Сурцуков Анатолий» и «Сурцуков А.» дают ОДИН ключ")
    void shortAndFullFormsMatch() {
        assertThat(AuthorNormalizer.toKey("Сурцуков Анатолий"))
                .isEqualTo(AuthorNormalizer.toKey("Сурцуков А."));
    }

    @Test
    @DisplayName("«Виноградов А.А.» и «Виноградов А. А.» дают один ключ")
    void spacingInsideInitialsDoesNotMatter() {
        assertThat(AuthorNormalizer.toKey("Виноградов А.А."))
                .isEqualTo(AuthorNormalizer.toKey("Виноградов А. А."))
                .isEqualTo("виноградов а. а.");
    }

    @Test
    @DisplayName("Двойная фамилия сохраняется целиком")
    void doubleSurnameIsKept() {
        assertThat(AuthorNormalizer.toKey("Умнова-Конюхова И.А."))
                .isEqualTo("умнова-конюхова и. а.");
    }

    @Test
    @DisplayName("Одно слово считается фамилией без инициалов")
    void singleWordIsSurname() {
        assertThat(AuthorNormalizer.toKey("Пушкин")).isEqualTo("пушкин");
    }

    @Test
    @DisplayName("Фамилия извлекается отдельно от ключа")
    void surnameExtracted() {
        assertThat(AuthorNormalizer.toSurname("Умнова-Конюхова И.А.")).isEqualTo("умнова-конюхова");
        assertThat(AuthorNormalizer.toSurname("Сурцуков Анатолий")).isEqualTo("сурцуков");
    }

    @Test
    @DisplayName("Пустое и пробельное значение не даёт ключа")
    void blankHasNoKey() {
        assertThat(AuthorNormalizer.toKey(null)).isEmpty();
        assertThat(AuthorNormalizer.toKey("   ")).isEmpty();
    }

    @Test
    @DisplayName("Несколько авторов в одном значении разделяются и точкой с запятой, и запятой")
    void separatorsAreBothHandled() {
        assertThat(AuthorExtractor.extract("Черняев А. Ю., Ланде А. А.", false))
                .extracting(AuthorExtractor.Author::raw)
                .containsExactly("Черняев А. Ю.", "Ланде А. А.");

        assertThat(AuthorExtractor.extract(
                "Умнова Ирина Анатольевна; Конюхова Ирина Анатольевна", false))
                .extracting(AuthorExtractor.Author::raw)
                .containsExactly("Умнова Ирина Анатольевна", "Конюхова Ирина Анатольевна");
    }

    @Test
    @DisplayName("Повтор одного автора внутри значения схлопывается")
    void duplicatesInsideOneValueCollapse() {
        List<AuthorExtractor.Author> authors =
                AuthorExtractor.extract("Иванов И. И., Иванов И. И.", false);
        assertThat(authors).hasSize(1);
    }

    @Test
    @DisplayName("Пустое значение даёт пустой список, а не исключение")
    void blankValueYieldsNoAuthors() {
        assertThat(AuthorExtractor.extract("   ", false)).isEmpty();
        assertThat(AuthorExtractor.extract(null, false)).isEmpty();
    }

    @Test
    @DisplayName("Пустые куски после разделителя отбрасываются")
    void emptyPiecesDropped() {
        assertThat(AuthorExtractor.extract("Иванов И.И.; ;  ;Петров П.П.,", false))
                .extracting(AuthorExtractor.Author::raw)
                .containsExactly("Иванов И.И.", "Петров П.П.");
    }
}