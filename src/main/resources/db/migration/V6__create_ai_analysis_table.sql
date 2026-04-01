CREATE TABLE IF NOT EXISTS ai_analysis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    property_id UUID NOT NULL REFERENCES properties(id),
    tipo_analisis VARCHAR(20) NOT NULL,
    tipo_servicio VARCHAR(20) NOT NULL,
    descripcion TEXT NOT NULL,
    impacto_estimado VARCHAR(255),
    periodo_analizado VARCHAR(20),
    datos_entrada TEXT,
    estado VARCHAR(20) NOT NULL DEFAULT 'PROCESANDO',
    calificacion_usuario INT CHECK (calificacion_usuario BETWEEN 1 AND 5),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_analysis_property_estado ON ai_analysis(property_id, estado);
CREATE INDEX IF NOT EXISTS idx_ai_analysis_tipo_servicio ON ai_analysis(property_id, tipo_servicio);
