-- Разные виды фоновых задач.
--
-- Загрузка каталога товаров не привязана к датам, поэтому date_from/date_to стали
-- необязательными, а сама задача помечается типом:
--   FINANCE — выгрузка начислений за период (прежнее поведение, значение по умолчанию);
--   CATALOG — выгрузка характеристик товаров.
--
-- Тип нужен ещё и потому, что блокировка у задач разная: у финансовой проверяется
-- пересечение дат, у каталога — достаточно, чтобы активная задача была одна.

ALTER TABLE sync_job
    ADD COLUMN job_type VARCHAR(16) NOT NULL DEFAULT 'FINANCE';

ALTER TABLE sync_job
    ALTER COLUMN date_from DROP NOT NULL,
    ALTER COLUMN date_to DROP NOT NULL;

ALTER TABLE sync_job
    ADD CONSTRAINT ck_sync_job_type CHECK (job_type IN ('FINANCE', 'CATALOG'));

-- Поиск активной задачи каталога: дат у неё нет, поэтому ищем по типу.
CREATE INDEX idx_sync_job_type_status ON sync_job (job_type, status);