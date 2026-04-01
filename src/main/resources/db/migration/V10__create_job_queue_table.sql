CREATE TABLE IF NOT EXISTS job_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_job VARCHAR(30) NOT NULL,
    payload TEXT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    intentos INT DEFAULT 0,
    max_intentos INT DEFAULT 3,
    proximo_intento TIMESTAMP,
    error_detalle TEXT,
    worker_id VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_job_queue_estado_proximo ON job_queue(estado, proximo_intento)
    WHERE estado IN ('PENDIENTE', 'EN_PROCESO');
