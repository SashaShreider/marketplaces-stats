package ru.analizer.integration.ozon;

/**
 * Идентификаторы характеристик товара OZON.
 *
 * <p>Не выдуманы: определены сопоставлением значений на настоящей выгрузке
 * {@code /v4/product/info/attributes} (108 товаров). Идентификаторы числовые и
 * простить нельзя, а названия OZON не отдаёт, поэтому значения зафиксированы здесь
 * как единственное место, где они живут.
 *
 * <p>У одного товара их 18, различных идентификаторов в выгрузке 89 — хранятся все,
 * а не только эти четыре.
 */
public final class OzonProductAttributes {

    /** «Автор» из карточки товара. Есть у 99 из 108 товаров выгрузки. */
    public static final long AUTHOR = 4182L;

    /** «Автор на обложке». Есть у 24 из 108 товаров. */
    public static final long COVER_AUTHOR = 105L;

    /** ISBN. Есть у 101 из 108 товаров. */
    public static final long ISBN = 4184L;

    private OzonProductAttributes() {
    }
}