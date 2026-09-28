-- Схема хранения сырых финансовых операций OZON.
--
-- Принципы:
--   * сохраняем всё, что отдаёт API, без потерь (raw_data JSONB + нормализованные таблицы);
--   * никаких заранее агрегированных таблиц — аналитика строится из операций;
--   * деньги только NUMERIC(19,4), никаких FLOAT/DOUBLE;
--   * единица хранения операции — finance_accrual (accrual_id из OZON).

-- ---------------------------------------------------------------------------
-- Маркетплейсы
-- ---------------------------------------------------------------------------
CREATE TABLE marketplace (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_marketplace_code UNIQUE (code)
);

-- ---------------------------------------------------------------------------
-- Аккаунты продавцов
-- api_key намеренно не хранится: берётся из переменной окружения OZON_API_KEY.
-- ---------------------------------------------------------------------------
CREATE TABLE seller_account (
    id                BIGSERIAL PRIMARY KEY,
    marketplace_id    BIGINT       NOT NULL REFERENCES marketplace (id),
    name              VARCHAR(255) NOT NULL,
    client_id         VARCHAR(255) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_seller_account_marketplace_client UNIQUE (marketplace_id, client_id)
);

-- ---------------------------------------------------------------------------
-- Справочник типов начислений (POST /v1/finance/accrual/types)
-- Список не захардкожен: OZON может добавлять новые type_id.
-- ---------------------------------------------------------------------------
CREATE TABLE accrual_type (
    id                BIGSERIAL PRIMARY KEY,
    marketplace_id    BIGINT       NOT NULL REFERENCES marketplace (id),
    external_type_id  INTEGER      NOT NULL,
    name              VARCHAR(255) NOT NULL,
    description       TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_accrual_type_marketplace_external UNIQUE (marketplace_id, external_type_id)
);

-- ---------------------------------------------------------------------------
-- Финансовая операция — центральная сущность.
-- external_id = accrual_id из OZON. unit_number НЕ уникален: на одно отправление
-- приходится несколько операций.
-- ---------------------------------------------------------------------------
CREATE TABLE finance_accrual (
    id                BIGSERIAL PRIMARY KEY,
    seller_account_id BIGINT       NOT NULL REFERENCES seller_account (id) ON DELETE CASCADE,
    external_id       BIGINT       NOT NULL,
    accrual_type_id   BIGINT           REFERENCES accrual_type (id),
    accrual_date      DATE         NOT NULL,
    unit_number       VARCHAR(255),
    accrued_category  VARCHAR(32)  NOT NULL,
    ozon_type_id      INTEGER,
    total_amount      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency          VARCHAR(8)   NOT NULL DEFAULT 'RUB',
    raw_data          JSONB        NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_finance_accrual_account_external UNIQUE (seller_account_id, external_id)
);

CREATE INDEX idx_finance_accrual_account_date
    ON finance_accrual (seller_account_id, accrual_date);
CREATE INDEX idx_finance_accrual_account_unit
    ON finance_accrual (seller_account_id, unit_number);
CREATE INDEX idx_finance_accrual_category
    ON finance_accrual (seller_account_id, accrued_category);
CREATE INDEX idx_finance_accrual_type
    ON finance_accrual (seller_account_id, ozon_type_id);

-- ---------------------------------------------------------------------------
-- POSTING: операции, привязанные к отправлению
-- ---------------------------------------------------------------------------
CREATE TABLE posting (
    id                 BIGSERIAL PRIMARY KEY,
    finance_accrual_id BIGINT      NOT NULL UNIQUE REFERENCES finance_accrual (id) ON DELETE CASCADE,
    delivery_schema    VARCHAR(32),
    delivery_speed     INTEGER,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE posting_product (
    id                BIGSERIAL PRIMARY KEY,
    posting_id        BIGINT         NOT NULL REFERENCES posting (id) ON DELETE CASCADE,
    sku               BIGINT         NOT NULL,
    quantity          INTEGER        NOT NULL DEFAULT 1,
    seller_price      NUMERIC(19, 4),
    sale_price        NUMERIC(19, 4),
    sale_amount       NUMERIC(19, 4),
    sale_commission   NUMERIC(19, 4),
    commission        NUMERIC(19, 4),
    commission_ratio  VARCHAR(64),
    coinvestment      NUMERIC(19, 4),
    bonus             NUMERIC(19, 4),
    currency          VARCHAR(8)     NOT NULL DEFAULT 'RUB',
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_posting_product_posting ON posting_product (posting_id);
CREATE INDEX idx_posting_product_sku ON posting_product (sku);

-- ---------------------------------------------------------------------------
-- Логистика: posting_product -> delivery.services[]
-- ---------------------------------------------------------------------------
CREATE TABLE delivery_service (
    id                 BIGSERIAL PRIMARY KEY,
    posting_product_id BIGINT         NOT NULL REFERENCES posting_product (id) ON DELETE CASCADE,
    type_id            INTEGER        NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency           VARCHAR(8)     NOT NULL DEFAULT 'RUB',
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_delivery_service_product ON delivery_service (posting_product_id);
CREATE INDEX idx_delivery_service_type ON delivery_service (type_id);

-- ---------------------------------------------------------------------------
-- ITEM: расходы, привязанные к SKU. Один ITEM может содержать
-- несколько SKU и несколько типов расходов.
-- ---------------------------------------------------------------------------
CREATE TABLE item_fee (
    id                 BIGSERIAL PRIMARY KEY,
    finance_accrual_id BIGINT       NOT NULL REFERENCES finance_accrual (id) ON DELETE CASCADE,
    sku                BIGINT       NOT NULL,
    quantity           INTEGER      NOT NULL DEFAULT 1,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_item_fee_accrual ON item_fee (finance_accrual_id);
CREATE INDEX idx_item_fee_sku ON item_fee (sku);

CREATE TABLE item_fee_detail (
    id          BIGSERIAL PRIMARY KEY,
    item_fee_id BIGINT         NOT NULL REFERENCES item_fee (id) ON DELETE CASCADE,
    type_id     INTEGER        NOT NULL,
    amount      NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency    VARCHAR(8)     NOT NULL DEFAULT 'RUB',
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX idx_item_fee_detail_fee ON item_fee_detail (item_fee_id);
CREATE INDEX idx_item_fee_detail_type ON item_fee_detail (type_id);

-- ---------------------------------------------------------------------------
-- NON_ITEM: расходы без привязки к SKU. Искусственно не распределяются.
-- ---------------------------------------------------------------------------
CREATE TABLE non_item_fee (
    id                 BIGSERIAL PRIMARY KEY,
    finance_accrual_id BIGINT       NOT NULL UNIQUE REFERENCES finance_accrual (id) ON DELETE CASCADE,
    type_id            INTEGER      NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency           VARCHAR(8)     NOT NULL DEFAULT 'RUB',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_non_item_fee_type ON non_item_fee (type_id);

-- ---------------------------------------------------------------------------
-- CONTAINER_FEES: начисления по контейнеру. Категория есть в актуальной схеме OZON,
-- в IMPLEMENTATION.md не описана, но терять её нельзя.
-- ---------------------------------------------------------------------------
CREATE TABLE container_fee (
    id                 BIGSERIAL PRIMARY KEY,
    finance_accrual_id BIGINT       NOT NULL REFERENCES finance_accrual (id) ON DELETE CASCADE,
    type_id            INTEGER      NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL DEFAULT 0,
    currency           VARCHAR(8)     NOT NULL DEFAULT 'RUB',
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_container_fee_accrual ON container_fee (finance_accrual_id);
CREATE INDEX idx_container_fee_type ON container_fee (type_id);
