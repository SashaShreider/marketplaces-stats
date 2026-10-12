package ru.analizer.integration.ozon.dto.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Тело запроса {@code POST /v3/product/list} (productv3.GetProductListV3Request).
 *
 * <p>Отдельный метод от {@code /v4/product/info/attributes} не из удобства, а потому
 * что у него другая роль: {@code v4} — источник характеристик и авторов, а {@code v3}
 * умеет отдавать то, чего {@code v4} не отдаёт никогда, — архивные товары.
 *
 * <p>Существенная особенность из документации OZON: если заполнить фильтр по
 * идентификаторам ({@code sku}, {@code offer_id} или {@code product_id}), товары
 * вернутся <b>независимо от видимости</b>, и остальные параметры заполнять не
 * обязательно. Это и есть способ точечно доставить архивный товар: не обходить
 * весь архив, а спросить конкретные SKU, которых не хватает.
 *
 * @param filter фильтр по товарам
 * @param lastId курсор пагинации; при первом запросе — пустая строка
 * @param limit  размер страницы; по спецификации от 1 до 1000
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductListV3Request(
        @JsonProperty("filter") ProductListV3Filter filter,
        @JsonProperty("last_id") String lastId,
        @JsonProperty("limit") Integer limit
) {

    /** Запрос по конкретным SKU: без указания видимости, иначе архивные не вернутся. */
    public static ProductListV3Request bySku(List<String> skus, int limit) {
        return new ProductListV3Request(
                new ProductListV3Filter(null, null, skus, null), "", limit);
    }

    /**
     * Фильтр {@code productv3.Filter}.
     *
     * <p>{@code visibility} — строка, а не массив, и значение {@code ALL} означает
     * «все товары, <b>кроме архивных</b>». Именно поэтому основной обход каталога
     * на {@code ALL} никогда не увидит архивный товар, сколько его ни повторяй.
     *
     * @param visibility состояние товара; {@code ARCHIVED}, {@code AUTO_ARCHIVED},
     *                   {@code MANUAL_ARCHIVED}, {@code SEASONAL_AUTO_ARCHIVED} —
     *                   архивные, но {@code null} в этом фильтре означает «любая
     *                   видимость», что как раз и нужно при поиске по SKU
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductListV3Filter(
            @JsonProperty("offer_id") List<String> offerId,
            @JsonProperty("product_id") List<String> productId,
            @JsonProperty("skus") List<String> skus,
            @JsonProperty("visibility") String visibility
    ) {
    }
}
