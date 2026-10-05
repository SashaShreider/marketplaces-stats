-- Каталог товаров маркетплейса.
--
-- Зачем это нужно:
--   * отчёт по товарам должен показывать ВСЕ товары аккаунта, а не только проданные;
--   * фильтр по автору невозможен без характеристик товара, а они лежат в атрибутах;
--   * характеристики OZON меняет без предупреждения, поэтому храним все, а не четыре
--     нужные сегодня: перезагрузка каталога стоит денег и лимитов.
--
-- Про «таблицу книг»: отдельной таблицы нет намеренно. В реальном каталоге этого
-- продавца из 108 товаров часть — бумага, календари, папки. Книжность — это признак
-- (атрибут 9236 «Печатная книга»), а не отдельная сущность. Отдельная таблица books
-- превратилась бы в подмножество этой с постоянными LEFT JOIN и проблемой «куда деть
-- не-книги».

-- ---------------------------------------------------------------------------
-- Товар
-- ---------------------------------------------------------------------------
CREATE TABLE ozon_product (
    id                      BIGSERIAL PRIMARY KEY,
    seller_account_id       BIGINT       NOT NULL REFERENCES seller_account (id) ON DELETE CASCADE,
    -- sku идентифицирует товар в OZON и жестко связан с начислениями: без единого
    -- идентификатора отчёт по товарам не собрать.
    sku                     BIGINT       NOT NULL,
    -- Внутренний product_id OZON. Не равен sku: в выгрузке 108 товаров
    -- id=861263 соответствует sku=174267969. Нужен, потому что курсор пагинации
    -- last_id кодирует именно product_id, а не sku.
    ozon_product_id         BIGINT,
    offer_id                VARCHAR(255),
    name                    TEXT,
    barcode                 VARCHAR(64),
    type_id                 BIGINT,
    description_category_id BIGINT,
    primary_image           TEXT,
    -- ISBN берём из атрибута 4184. Дублирует строку в ozon_product_attribute, но это
    -- единственный признак, который пользователь ищет глазами, и подтягивать его
    -- джойном на каждую строку отчёта незачем.
    isbn                    VARCHAR(64),
    -- Размеры и вес приведены к единицам: в ответе OZON они приходят со своими
    -- (weight_unit, dimension_unit), и без приведения к общей единице сравнивать
    -- товары между собой нельзя.
    weight_grams            INTEGER,
    width_mm                INTEGER,
    height_mm               INTEGER,
    depth_mm                INTEGER,
    model_id                BIGINT,
    -- Исходный JSON товара целиком. Без него неизвестный атрибут пришлось бы
    -- догадывать, а переинтерпретировать данные при смене правил было бы нечем.
    raw_data                JSONB        NOT NULL,
    first_seen_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_synced_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_ozon_product_account_sku UNIQUE (seller_account_id, sku)
);

CREATE INDEX idx_ozon_product_account_offer ON ozon_product (seller_account_id, offer_id);
CREATE INDEX idx_ozon_product_account_name ON ozon_product (seller_account_id, name);

-- ---------------------------------------------------------------------------
-- Характеристики товара
--
-- Атрибутов на товар в выгрузке 18, всего различных id — 89. Храним все:
-- сегодня нужны четыре (автор, автор на обложке, ISBN и др.), завтра понадобится
-- пятый, и перезагружать каталог ради него не захочется.
-- ---------------------------------------------------------------------------
CREATE TABLE ozon_product_attribute (
    id                  BIGSERIAL PRIMARY KEY,
    seller_account_id   BIGINT       NOT NULL REFERENCES seller_account (id) ON DELETE CASCADE,
    sku                 BIGINT       NOT NULL,
    attribute_id        BIGINT       NOT NULL,
    -- Порядок значений внутри атрибута. В текущей выгрузке у каждого атрибута ровно
    -- одно значение, но по спецификации values — массив, и порядок в нём значим
    -- (например, для набора значений вроде цветов).
    position            INTEGER      NOT NULL,
    value               TEXT,
    dictionary_value_id BIGINT,
    CONSTRAINT uq_ozon_product_attribute
        UNIQUE (seller_account_id, sku, attribute_id, position),
    -- Ссылка на товар: если товар удалён из каталога, его характеристики тоже
    -- не должны остаться висеть осиротевшими.
    CONSTRAINT fk_product_attribute_product
        FOREIGN KEY (seller_account_id, sku)
        REFERENCES ozon_product (seller_account_id, sku) ON DELETE CASCADE
);

-- Поиск значения по паре «атрибут + товар»: так поднимается ISBN и авторы.
CREATE INDEX idx_product_attribute_lookup
    ON ozon_product_attribute (seller_account_id, attribute_id, sku);