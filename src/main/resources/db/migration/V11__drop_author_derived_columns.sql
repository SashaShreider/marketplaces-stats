-- Удаление производных множеств авторов: фильтр теперь ищет по product_author напрямую.
--
-- В V6 на товаре лежали author_keys («фамилия + инициалы» в нижнем регистре) и
-- author_surnames (только фамилии). Нужны они были для нестрогого поиска: ввод
-- «Сурцуков» находил и «Сурцуков А.», и «Сурцуков Анатолий».
--
-- Теперь контракт строгий — ищется ровно то, как автор назван в карточке, — и
-- производные массивы стали лишними: значение, по которому сравнивают, и есть
-- author_raw в product_author. Держать вторую копию этих данных незачем, а
-- поддерживать её пришлось бы на каждом импорте каталога.
--
-- Данные не теряются: product_author остаётся источником истины, он заполняется
-- при импорте каталога и здесь не меняется. Переимпортировать каталог после
-- миграции не нужно — фильтр работает по тому, что уже есть.
--
-- idx_product_author_sku тоже уходит: он дублировал префикс уникального индекса
-- uq_product_author (seller_account_id, sku, author_raw, source), а тот обслуживает
-- и поиск по (account, sku), и удаление авторов товара.

DROP INDEX IF EXISTS idx_ozon_product_author_keys;
DROP INDEX IF EXISTS idx_ozon_product_author_surnames;
DROP INDEX IF EXISTS idx_product_author_sku;

ALTER TABLE ozon_product
    DROP COLUMN IF EXISTS author_keys,
    DROP COLUMN IF EXISTS author_surnames;

-- Подсказка фильтра и сам фильтр идут по значению автора. Уникальный индекс
-- product_author покрывает префикс (seller_account_id, sku), и планировщик берёт
-- его и для поиска по author_raw, но явный индекс отвечает на запрос прямо и
-- страхует от смены планировщика на худший план.
CREATE INDEX IF NOT EXISTS idx_product_author_value
    ON product_author (seller_account_id, author_raw, sku);