CREATE TABLE IF NOT EXISTS user_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id),
    dias_anticipacion_alerta INT DEFAULT 5,
    metodo_pago_default VARCHAR(20),
    presupuesto_mensual_agua NUMERIC(19,4),
    presupuesto_mensual_energia NUMERIC(19,4),
    presupuesto_mensual_gas NUMERIC(19,4),
    presupuesto_mensual_internet NUMERIC(19,4),
    notificaciones_email BOOLEAN DEFAULT TRUE,
    notificaciones_push BOOLEAN DEFAULT TRUE,
    moneda VARCHAR(3) DEFAULT 'COP',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);
