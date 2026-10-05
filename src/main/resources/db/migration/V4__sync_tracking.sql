-- Учёт синхронизации по дням и по задачам.
--
-- Зачем это нужно:
--   * отличить «день загружен и начислений действительно не было» от «день не загружен»;
--   * знать, какие дни в выбранном периоде ещё нужно догрузить;
--   * понимать, когда данные по дню можно считать окончательными.
--
-- Про «окончательность»: начисления за текущий день продолжают приходить в течение суток,
-- и даже за вчерашний день могут появиться позже (возвраты, корректировки). Поэтому
-- день считается окончательным только когда он старше окна зрелости.
--
-- Про измерение, а не догадку: при каждой повторной загрузке дня сравнивается сумма
-- начислений с предыдущей. Пока сумма меняется, день не окончателен. Это позволяет
-- выбрать разумное окно зрелости по фактическим наблюдениям, а не по предположению.

-- ---------------------------------------------------------------------------
-- Учёт по дням: по одному дню на строку
-- ---------------------------------------------------------------------------
CREATE TABLE sync_day (
    id                BIGSERIAL PRIMARY KEY,
    seller_account_id BIGINT       NOT NULL REFERENCES seller_account (id) ON DELETE CASCADE,
    day               DATE         NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    is_final          BOOLEAN      NOT NULL DEFAULT FALSE,
    accrual_count     INTEGER      NOT NULL DEFAULT 0,
    total_amount      NUMERIC(19, 4),
    first_synced_at   TIMESTAMPTZ,
    last_synced_at    TIMESTAMPTZ,
    -- Момент, когда данные дня перестали меняться при повторной загрузке.
    unchanged_since   TIMESTAMPTZ,
    -- Сколько раз повторная загрузка обнаружила изменение суммы. Ненулевое значение
    -- означает, что день ещё «созревал» и его нельзя было считать окончательным.
    change_count      INTEGER      NOT NULL DEFAULT 0,
    last_error        TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_sync_day_account_day UNIQUE (seller_account_id, day),
    CONSTRAINT ck_sync_day_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'DONE', 'FAILED'))
);

CREATE INDEX idx_sync_day_account_day ON sync_day (seller_account_id, day);
CREATE INDEX idx_sync_day_status ON sync_day (status);

-- ---------------------------------------------------------------------------
-- Фоновые задачи загрузки
-- ---------------------------------------------------------------------------
CREATE TABLE sync_job (
    id                BIGSERIAL PRIMARY KEY,
    seller_account_id BIGINT       NOT NULL REFERENCES seller_account (id) ON DELETE CASCADE,
    marketplace_code  VARCHAR(64)  NOT NULL,
    date_from         DATE         NOT NULL,
    date_to           DATE         NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    total_days        INTEGER      NOT NULL DEFAULT 0,
    done_days         INTEGER      NOT NULL DEFAULT 0,
    skipped_days      INTEGER      NOT NULL DEFAULT 0,
    failed_days       INTEGER      NOT NULL DEFAULT 0,
    current_day       DATE,
    error             TEXT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at        TIMESTAMPTZ,
    finished_at       TIMESTAMPTZ,
    CONSTRAINT ck_sync_job_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_sync_job_status ON sync_job (status);
CREATE INDEX idx_sync_job_created ON sync_job (created_at DESC);