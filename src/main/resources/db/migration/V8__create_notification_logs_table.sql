CREATE TABLE IF NOT EXISTS notification_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    tipo VARCHAR(30) NOT NULL,
    canal VARCHAR(10) NOT NULL,
    estado VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE',
    asunto VARCHAR(255),
    cuerpo_resumen VARCHAR(500),
    referencia_id UUID,
    intentos INT DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_notification_logs_user_id ON notification_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_notification_logs_estado ON notification_logs(estado) WHERE estado = 'PENDIENTE';
