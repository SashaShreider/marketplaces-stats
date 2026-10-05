package ru.analizer.catalog;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Разбор значения атрибута «Автор» на отдельных авторов.
 *
 * <p>Класс чистый. Правила выведены из настоящей выгрузки: у OZON значение — одна
 * строка, а не массив, и разделители у двух атрибутов разные:
 * <ul>
 *   <li>4182 «Автор» — «Черняев А. Ю., Ланде А. А.» (запятые);</li>
 *   <li>105 «Автор на обложке» — «Умнова И.; Конюхова И.; Умнова-Конюхова И.» (точки
 *       с запятой).</li>
 * </ul>
 * Поэтому делим и по тому, и по другому.
 *
 * <h2>Известное ограничение</h2>
 * Фамилия «Иванов» с именем через запятую («Иванов, Иван») будет разбита на двух
 * авторов. В выгрузке этого продавца таких случаев нет, но правило само по себе
 * неоднозначно — в отчёте видно исходное значение, и ошибку можно заметить.
 */
public final class AuthorExtractor {

    /** Разделители приходят в обоих видах, поэтому делим по обоим. */
    private static final String SEPARATORS = "[;,]";

    private AuthorExtractor() {
    }

    /**
     * @param raw значение атрибута как есть; {@code null} и пустая строка допустимы
     * @param primary признак основного источника: {@code true} для 4182, когда он есть
     * @return авторы в порядке появления, без повторов
     */
    public static List<Author> extract(String raw, boolean primary) {
        List<Author> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        // LinkedHashSet: сохраняем порядок появления и убираем повторы — в выгрузке
        // один и тот же человек встречается и в 4182, и в 105.
        Set<String> seen = new LinkedHashSet<>();
        int position = 0;
        for (String piece : raw.split(SEPARATORS)) {
            String name = piece.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (seen.add(AuthorNormalizer.toKey(name))) {
                result.add(new Author(name, AuthorNormalizer.toKey(name), primary, position++));
            }
        }
        return result;
    }

    /**
     * Один автор в исходном виде.
     *
     * @param raw значение как вернул OZON — для показа пользователю
     * @param key сведённый ключ — для фильтра
     */
    public record Author(String raw, String key, boolean primary, int position) {
    }
}