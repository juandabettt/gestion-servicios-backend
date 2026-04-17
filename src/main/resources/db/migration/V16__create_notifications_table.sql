CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL REFERENCES users(id),
    factura_id UUID NOT NULL REFERENCES invoices(id),
    tipo VARCHAR(30) NOT NULL,
    titulo VARCHAR(255) NOT NULL,
    mensaje VARCHAR(500) NOT NULL,
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_notifications_factura_tipo ON notifications(factura_id, tipo);
CREATE INDEX IF NOT EXISTS idx_notifications_usuario_id ON notifications(usuario_id);
CREATE INDEX IF NOT EXISTS idx_notifications_usuario_no_leidas ON notifications(usuario_id, leida) WHERE leida = FALSE;
