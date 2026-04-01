CREATE TABLE IF NOT EXISTS provider_companies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre VARCHAR(150) NOT NULL,
    nit VARCHAR(20) UNIQUE NOT NULL,
    tipo_servicio VARCHAR(20) NOT NULL,
    codigo_convenio_recaudo VARCHAR(50) NOT NULL,
    url_portal VARCHAR(255),
    telefono_soporte VARCHAR(20),
    ciudad_cobertura VARCHAR(100),
    ciclo_facturacion_dias INT DEFAULT 30,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_providers_tipo_servicio ON provider_companies(tipo_servicio) WHERE activo = TRUE;
CREATE INDEX IF NOT EXISTS idx_providers_ciudad ON provider_companies(ciudad_cobertura) WHERE activo = TRUE;
