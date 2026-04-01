-- La tabla audit_log es append-only: sin updated_at ni soft delete
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    accion VARCHAR(100) NOT NULL,
    entidad VARCHAR(50),
    entidad_id UUID,
    ip_origen VARCHAR(45),
    user_agent VARCHAR(255),
    resultado VARCHAR(10) NOT NULL,
    detalle VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_user_id ON audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_accion ON audit_logs(accion);
