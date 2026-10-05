package ru.analizer.catalog.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Сведение авторов к сравнимому виду: «фамилия + инициалы» в нижнем регистре.
 *
 * <p>Класс чистый: ни базы, ни Spring. Нужен потому, что имена у OZON приходят в
 * разных форматах, и без сведения фильтр «автор = Сурцуков» молча терял бы товары.
 *
 * <h2>Зачем нижний регистр</h2>
 * Индекс в PostgreSQL чувствителен к регистру, а «Сурцуков А.» и «сурцуков а.» —
 * один и тот же человек. Показывать пользователю будем исходное значение из
 * {@code product_author}, эти ключи — только для поиска.
 *
 * <h2>Известное ограничение</h2>
 * Сведение работает по форме, а не по смыслу, поэтому разные написания одного
 * человека останутся разными ключами. В выгрузке есть товар, где «автор на обложке»
 * перечисляет «Умнова Ирина Анатольевна; Конюхова Ирина Анатольевна; Умнова-Конюхова
 * Ирина Анатольевна» — это один человек, и получит три ключа: «умнова и.а.»,
 * «конюхова и.а.», «умнова-конюхова и.а.». Лечится таблицей ручных склеек, а не
 * усложнением разбора: автоматика тут только угадывает.
 */
public final class AuthorNormalizer {

    private AuthorNormalizer() {
    }

    /**
     * Ключ для фильтра: фамилия и инициалы в нижнем регистре.
     *
     * @param raw значение атрибута «Автор» или «Автор на обложке» как есть
     * @return пустая строка, если разбирать нечего
     */
    public static String toKey(String raw) {
        String surname = toSurname(raw);
        if (surname.isEmpty()) {
            return "";
        }
        StringBuilder key = new StringBuilder(surname);
        for (String initial : initialsOf(raw)) {
            key.append(' ').append(initial);
        }
        return key.toString();
    }

    /** Фамилия в нижнем регистре: первое слово значения. */
    public static String toSurname(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String first = raw.trim().split("\\s+")[0];
        return first.toLowerCase(Locale.ROOT);
    }

    /**
     * Инициалы из всех слов, кроме первого.
     *
     * <p>Слово даёт один инициал — первую букву, — если это не набор «А.А.», где
     * инициалов столько же, сколько букв. Так «А.А.» разбирается в два инициала,
     * а не в один «А».
     */
    private static List<String> initialsOf(String raw) {
        List<String> initials = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return initials;
        }
        String[] words = raw.trim().split("\\s+");
        for (int i = 1; i < words.length; i++) {
            addInitialsOfWord(words[i], initials);
        }
        return initials;
    }

    private static void addInitialsOfWord(String word, List<String> initials) {
        String cleaned = word.replace(".", "");
        if (cleaned.isEmpty()) {
            // «Иванов А. .» — значение с лишней точкой не должно терять инициал.
            return;
        }
        if (isDottedAbbreviation(word)) {
            for (int i = 0; i < cleaned.length(); i++) {
                initials.add(Character.toLowerCase(cleaned.charAt(i)) + ".");
            }
            return;
        }
        initials.add(Character.toLowerCase(cleaned.charAt(0)) + ".");
    }

    /** «А.А.» или «А. А.» — сокращение из нескольких инициалов, а не одно слово. */
    private static boolean isDottedAbbreviation(String word) {
        return word.length() > 1 && word.indexOf('.') >= 0
                && word.chars().noneMatch(c -> !Character.isLetterOrDigit(c) && c != '.');
    }
}