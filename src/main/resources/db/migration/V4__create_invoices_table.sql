CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id),
    proveedor_id UUID NOT NULL REFERENCES provider_companies(id),
    numero_referencia VARCHAR(500) NOT NULL,
    fecha_emision DATE NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    monto_total NUMERIC(19,4) NOT NULL,
    consumo_unidad NUMERIC(19,4),
    unidad_medida VARCHAR(20),
    periodo_facturado VARCHAR(20),
    estado VARCHAR(30) NOT NULL DEFAULT 'PROCESANDO_OCR',
    url_foto_factura VARCHAR(500),
    ocr_datos_raw TEXT,
    ocr_confianza DECIMAL(5,2),
    ingreso_manual BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

-- Indices obligatorios segun MASTER_PROMPT
CREATE INDEX IF NOT EXISTS idx_invoices_property_estado ON invoices(property_id, estado) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_invoices_vencimiento_estado ON invoices(fecha_vencimiento, estado) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_invoices_proveedor_periodo ON invoices(proveedor_id, periodo_facturado) WHERE deleted_at IS NULL;
