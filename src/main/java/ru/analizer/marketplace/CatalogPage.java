package ru.analizer.marketplace;

import java.util.List;

/**
 * Страница каталога товаров.
 *
 * <p><b>Про {@code lastId}.</b> Он приходит непустым даже на последней странице и
 * указывает на последний товар этой страницы. Проверять его на пустоту, чтобы решить,
 * есть ли следующая страница, нельзя: цикл не завершился бы никогда. Признак конца
 * разбирает {@link CatalogPager}.
 *
 * @param total сколько товаров у продавца всего, по данным OZON
 */
public record CatalogPage(List<ProductEntry> products, int total, String lastId) {

    public CatalogPage {
        products = products == null ? List.of() : List.copyOf(products);
    }
}