-- Redesign auto_pay_rules: link directly to user with tipoServicio filter
TRUNCATE TABLE auto_pay_rules;

DROP INDEX IF EXISTS idx_auto_pay_rules_property;
DROP INDEX IF EXISTS idx_auto_pay_rules_unique;

ALTER TABLE auto_pay_rules
    DROP COLUMN IF EXISTS property_id,
    DROP COLUMN IF EXISTS proveedor_id,
    DROP COLUMN IF EXISTS metodo_pago,
    DROP COLUMN IF EXISTS activo;

ALTER TABLE auto_pay_rules
    ADD COLUMN IF NOT EXISTS usuario_id   UUID         NOT NULL REFERENCES users(id),
    ADD COLUMN IF NOT EXISTS nombre       VARCHAR(100) NOT NULL,
    ADD COLUMN IF NOT EXISTS tipo_servicio VARCHAR(50) NOT NULL DEFAULT 'TODOS',
    ADD COLUMN IF NOT EXISTS activa       BOOLEAN      NOT NULL DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS total_pagos_realizados INTEGER NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_auto_pay_rules_usuario
    ON auto_pay_rules(usuario_id) WHERE deleted_at IS NULL AND activa = TRUE;
