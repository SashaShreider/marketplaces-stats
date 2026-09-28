-- Базовый справочник маркетплейсов.
INSERT INTO marketplace (code, name)
VALUES ('OZON', 'OZON')
ON CONFLICT (code) DO NOTHING;
