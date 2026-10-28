package ru.analizer.analytics.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Отбор, которым ограничен отчёт: все товары, один автор или список товаров.
 *
 * <p>Отчёт по магазину и отчёт по трём книгам — разные вопросы, и путать их нельзя:
 * продавец смотрит на «Доходы» и должен понимать, весь это магазин или выбранная
 * полка. Поэтому вместе с отбором всегда едет его подпись ({@link #label()}), а
 * вместе с ней — признак {@link #scoped()}: по нему клиент понимает, что часть
 * расходов в цифры не вошла.
 *
 * <h2>Почему отбор применяется в памяти, а не в SQL</h2>
 * Дневной отчёт и так забирает все операции периода одним проходом и раскладывает
 * их по дням в Java: запрос по датам уже отбирает десятки тысяч строк, а не
 * миллионы. Добавить к ним ещё одно условие — это отсеять часть уже полученного
 * списка, а не сходить в базу второй раз. Если период вырастет настолько, что
 * выборка станет узким местом, отбор переедет в SQL, и это будет видно по профилю.
 *
 * <h2>Расходы без товара</h2>
 * У NON_ITEM и CONTAINER нет SKU, поэтому при отборе они в отчёт не попадают —
 * распределить их между товарами можно только выдумав правило. Сумма отброшенного
 * возвращается отдельно ({@code unallocatedExpenses}), чтобы усечённая прибыль не
 * выглядела полной.
 */
public record ReportScope(String author, Set<Long> skus) {

    public ReportScope {
        skus = skus == null ? Set.of() : new LinkedHashSet<>(skus);
    }

    /** Весь магазин: ни автор, ни товары не выбраны. */
    public static ReportScope all() {
        return new ReportScope(null, Set.of());
    }

    /**
     * Отбор по автору.
     *
     * @param author имя ровно в том написании, в каком оно в карточке товара;
     *               {@code null} или пустая строка — без ограничения
     */
    public static ReportScope ofAuthor(String author) {
        String value = author == null ? null : author.trim();
        return new ReportScope(value == null || value.isEmpty() ? null : value, Set.of());
    }

    /**
     * Отбор по конкретным товарам.
     *
     * <p>Заданные товары сильнее автора: выбрав три книги, пользователь уже ответил
     * на вопрос «чьи это товары», и второй отбор только сузил бы список непонятно
     * почему. То же правило действует в отчёте по товарам.
     *
     * @param skus номера товаров; пустой список — без ограничения
     */
    public static ReportScope ofSkus(List<Long> skus) {
        Set<Long> values = skus == null ? Set.of()
                : skus.stream().filter(java.util.Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return new ReportScope(null, values);
    }

    /** Ограничен ли отчёт отбором. */
    public boolean scoped() {
        return author != null || !skus.isEmpty();
    }

    /**
     * Подпись отбора для интерфейса: «Все товары», имя автора или «SKU 123, 456».
     *
     * <p>Длинный список товаров не перечисляется целиком: в шапке отчёта он занял бы
     * всю строку и вытеснил период.
     */
    public String label() {
        if (!skus.isEmpty()) {
            String first = skus.stream().limit(3).map(String::valueOf)
                    .collect(Collectors.joining(", "));
            int rest = skus.size() - Math.min(3, skus.size());
            return rest == 0 ? "SKU " + first : "SKU " + first + " и ещё " + rest;
        }
        return author != null ? author : "Все товары";
    }

    /**
     * Попадает ли строка товара в отбор.
     *
     * <p>Отбор по автору проверяется по готовому множеству SKU, а не по
     * {@code product_author}: список товаров автора приходит одним запросом, и
     * повторять подзапрос на каждую строку незачем.
     *
     * @param sku         номер товара
     * @param authorSkus  SKU выбранного автора; {@code null}, если отбора по автору нет
     */
    public boolean accepts(Long sku, Set<Long> authorSkus) {
        if (!skus.isEmpty()) {
            return sku != null && skus.contains(sku);
        }
        if (author != null) {
            return sku != null && authorSkus != null && authorSkus.contains(sku);
        }
        return true;
    }

    /** Попадает ли начисление в отбор. Начисления без SKU отсекаются при любом отборе. */
    public boolean acceptsFee(FeeFact fee, Set<Long> authorSkus) {
        return accepts(fee.sku(), authorSkus);
    }
}
