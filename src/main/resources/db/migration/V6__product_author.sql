-- Авторы товара и индексы для фильтра по ним.
--
-- Зачем две таблицы, а не одна колонка:
--   * product_author хранит значение КАК ПРИШЛО от OZON. Это источник истины.
--     Формат имён скачет: «Сурцуков А.», «Сурцуков Анатолий», «Виноградов А.А.»,
--     «Шибанов Георгий Петрович». Строка «как дано» нужна для показа, и она же
--     переживёт любую смену правил нормализации.
--   * author_keys / author_surnames на самом товаре — производные множества для
--     индексированной фильтрации. Их можно пересчитать в любой момент из
--     product_author, ничего не теряя.
--
-- Про дубликаты в author_keys: множество убирает повтор, когда одно и то же лицо
-- указано в двух полях — «Сурцуков А.» (4182) и «Сурцуков Анатолий» (105) дают
-- один элемент «сурцуков а.». Но набор ОНОГО человека может состоять из РАЗНЫХ
-- фамилий: в выгрузке есть товар, где поле «автор на обложке» перечисляет
-- «Умнова Ирина Анатольевна; Конюхова Ирина Анатольевна; Умнова-Конюхова Ирина
-- Анатольевна» — это один человек, и после сведения к «фамилия + инициалы» он даст
-- три разных ключа. Автоматика этого не лечит: понадобится таблица ручных склеек
-- (см. AuthorNormalizer — там же отмечено ограничение).

-- ---------------------------------------------------------------------------
-- Авторы товара: по строке на одного автора в одном поле
-- ---------------------------------------------------------------------------
CREATE TABLE product_author (
    id                BIGSERIAL PRIMARY KEY,
    seller_account_id BIGINT       NOT NULL REFERENCES seller_account (id) ON DELETE CASCADE,
    sku               BIGINT       NOT NULL,
    -- Значение ровно как вернул OZON, без нормализации.
    author_raw        TEXT         NOT NULL,
    -- DECLARED — атрибут 4182 «Автор» (карточка товара).
    -- COVER     — атрибут 105 «Автор на обложке».
    -- Разделение нужно, чтобы отчёт мог показать, откуда взялось имя: продавец
    -- может не заполнить карточку, и тогда имя на обложке — единственный источник.
    source            VARCHAR(16)  NOT NULL,
    is_primary        BOOLEAN      NOT NULL DEFAULT FALSE,
    position          INTEGER      NOT NULL,
    CONSTRAINT ck_product_author_source CHECK (source IN ('DECLARED', 'COVER')),
    CONSTRAINT uq_product_author
        UNIQUE (seller_account_id, sku, author_raw, source),
    CONSTRAINT fk_product_author_product
        FOREIGN KEY (seller_account_id, sku)
        REFERENCES ozon_product (seller_account_id, sku) ON DELETE CASCADE
);

CREATE INDEX idx_product_author_sku ON product_author (seller_account_id, sku);

-- ---------------------------------------------------------------------------
-- Производные множества на товаре — ради быстрого фильтра
--
-- Ключи приведены к нижнему регистру: индекс в PostgreSQL чувствителен к регистру,
-- а имя, набранное продавцом в карточке, может отличаться регистом от того же имени
-- в другом товаре. Показывать пользователю будем author_raw из product_author,
-- а эти массивы — только для поиска.
-- ---------------------------------------------------------------------------
ALTER TABLE ozon_product
    ADD COLUMN author_keys TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN author_surnames TEXT[] NOT NULL DEFAULT '{}';

CREATE INDEX idx_ozon_product_author_keys ON ozon_product USING GIN (author_keys);
CREATE INDEX idx_ozon_product_author_surnames ON ozon_product USING GIN (author_surnames);