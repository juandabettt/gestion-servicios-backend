CREATE TABLE IF NOT EXISTS auto_pay_rules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id),
    proveedor_id UUID NOT NULL REFERENCES provider_companies(id),
    metodo_pago VARCHAR(20) NOT NULL,
    dias_antes_vencimiento INT NOT NULL DEFAULT 2,
    monto_maximo NUMERIC(19,4),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    ultima_ejecucion TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auto_pay_rules_property ON auto_pay_rules(property_id) WHERE deleted_at IS NULL AND activo = TRUE;
CREATE UNIQUE INDEX IF NOT EXISTS idx_auto_pay_rules_unique ON auto_pay_rules(property_id, proveedor_id) WHERE deleted_at IS NULL AND activo = TRUE;
