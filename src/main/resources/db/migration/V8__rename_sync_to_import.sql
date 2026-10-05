-- Переименование «sync» в «import».
--
-- Зачем это нужно:
--   * слово sync обозначало действие, а не то, что мы храним. Теперь мы храним
--     ПРОГОН загрузки, и он продолжается, когда HTTP-запрос давно закончился;
--   * сущности и API обязаны называться так же, как таблицы, иначе половина
--     кода говорит об импортах, а половина — о синхронизации;
--   * имена sync_job и sync_day тянули за собой слова Sync во всех 323 упоминаниях
--     в коде, поэтому переименование выполнено целиком, а не только в схеме.
--
-- Данные не переносятся: значения в столбцах status и job_type остаются прежними.
-- Меняются только имена таблиц, индексов и ограничений.

-- ---------------------------------------------------------------------------
-- Таблицы
-- ---------------------------------------------------------------------------
ALTER TABLE sync_job  RENAME TO import_run;
ALTER TABLE sync_day  RENAME TO imported_day;

-- ---------------------------------------------------------------------------
-- Индексы
--
-- Имена индексов переименовываются отдельно: PostgreSQL не переименовывает их
-- вместе с таблицей, и оставшиеся idx_sync_* в схеме сбивали бы с толку при
-- разборе плана запроса.
-- ---------------------------------------------------------------------------
ALTER INDEX idx_sync_job_status        RENAME TO idx_import_run_status;
ALTER INDEX idx_sync_job_created       RENAME TO idx_import_run_created;
ALTER INDEX idx_sync_job_type_status   RENAME TO idx_import_run_type_state;

ALTER INDEX idx_sync_day_account_day   RENAME TO idx_imported_day_account_day;
ALTER INDEX idx_sync_day_status        RENAME TO idx_imported_day_state;

-- ---------------------------------------------------------------------------
-- Колонки прогона
--
-- *_days называли единицу работы «днем», но у импорта каталога единица работы —
-- товар, а не день. Переименование в *_units делает колонку пригодной для обоих
-- видов импорта; что именно считается, видно по полю import_type.
-- ---------------------------------------------------------------------------
ALTER TABLE import_run RENAME COLUMN job_type      TO import_type;
ALTER TABLE import_run RENAME COLUMN total_days    TO total_units;
ALTER TABLE import_run RENAME COLUMN done_days     TO done_units;
ALTER TABLE import_run RENAME COLUMN skipped_days  TO skipped_units;
ALTER TABLE import_run RENAME COLUMN failed_days   TO failed_units;
ALTER TABLE import_run RENAME COLUMN current_day   TO current_unit;

-- Тип тоже меняется: колонка хранила дату в работе, а у импорта каталога в работе
-- стоит SKU. Текст вместо даты покрывает оба случая, а приведение выполняется
-- явным USING, потому что PostgreSQL не находит преобразование сам.
ALTER TABLE import_run
    ALTER COLUMN current_unit TYPE VARCHAR(255) USING current_unit::text;

-- ---------------------------------------------------------------------------
-- Ограничения
-- ---------------------------------------------------------------------------
ALTER TABLE imported_day
    RENAME CONSTRAINT uq_sync_day_account_day TO uq_imported_day_account_day;

ALTER TABLE imported_day
    RENAME CONSTRAINT ck_sync_day_status TO ck_imported_day_state;

ALTER TABLE import_run
    RENAME CONSTRAINT ck_sync_job_status TO ck_import_run_state;

ALTER TABLE import_run
    RENAME CONSTRAINT ck_sync_job_type TO ck_import_run_type;