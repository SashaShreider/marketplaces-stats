package ru.analizer.analytics.infrastructure.filter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Фильтр по автору — по полям «Автор» (4182) и «Автор на обложке» (105).
 *
 * <p>Оба поля уже разобраны при импорте на отдельных авторов и лежат в
 * {@code product_author} отдельной строкой на автора. Поэтому фильтру не нужно
 * ничего понимать в написании имён: он ищет точное совпадение с тем текстом,
 * которым его назвал продавец.
 *
 * <h2>Почему точное совпадение</h2>
 * У одного продавца один и тот же человек в разных товарах назван по-разному:
 * «Сурцуков А.», «Сурцуков Анатолий», «А.В. Сурцуков», «Анатолий Васильевич
 * Сурцуков». Сведение к «фамилия + инициалы» склеивало бы их, но только угадывает:
 * оно и не различило бы «Герланец Валерий» и «Герланец Валерий Петрович», и склеило
 * бы разных людей с общей фамилией. В отчёте о продажах лишний товар хуже
 * неполного списка, поэтому фильтр строгий.
 *
 * <p>Несколько авторов в одной карточке — единственное исключение, и оно
 * получается само собой: «Черняев А. Ю., Ланде А. А.» разбит при импорте на две
 * строки, и поиск любой из них находит товар. Искать надо «Ланде А. А.», а не всю
 * строку целиком.
 *
 * <p>Значение сравнивается как есть, без приведения регистра: пользователь выбирает
 * автора из подсказки {@code GET /data/authors}, а там — ровно те строки, что лежат
 * в базе.
 */
public final class ProductAuthorFilter implements ProductAttributeFilter {

    private static final String VALUE = "authorValue";

    private final String value;

    private ProductAuthorFilter(String value) {
        this.value = value;
    }

    /**
     * Фильтр по одному значению автора.
     *
     * @param value имя автора как его написал продавец; {@code null} или пустая строка
     *              означают «без фильтра»
     */
    public static ProductAuthorFilter byValue(String value) {
        return new ProductAuthorFilter(value == null ? "" : value.trim());
    }

    @Override
    public String predicate(String alias) {
        if (value.isEmpty()) {
            return null;
        }
        // exists, а не массив на товаре: искомое имя лежит в отдельной строке, и
        // подзапрос по ней идёт по индексу (seller_account_id, sku). Держать ради
        // этого ещё и массив на каждом товаре незачем — значение и так выводится из
        // product_author, а таблица остаётся единственным источником истины.
        return "exists (select 1 from product_author pa"
                + " where pa.seller_account_id = " + alias + ".seller_account_id"
                + " and pa.sku = " + alias + ".sku"
                + " and pa.author_raw = :" + VALUE + ")";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put(VALUE, value);
        return parameters;
    }
}