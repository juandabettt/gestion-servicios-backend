CREATE TABLE IF NOT EXISTS payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    factura_id UUID NOT NULL REFERENCES invoices(id),
    monto_transaccion NUMERIC(19,4) NOT NULL,
    metodo_pago VARCHAR(20) NOT NULL,
    banco_origen VARCHAR(100),
    id_transaccion_pasarela VARCHAR(500),
    estado_transaccion VARCHAR(20) NOT NULL DEFAULT 'INICIADA',
    url_redireccion_pse VARCHAR(500),
    fecha_confirmacion TIMESTAMP,
    intentos_webhook INT DEFAULT 0,
    idempotency_key VARCHAR(255) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_payment_transactions_factura ON payment_transactions(factura_id);
CREATE INDEX IF NOT EXISTS idx_payment_transactions_estado ON payment_transactions(estado_transaccion) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_payment_transactions_idempotency ON payment_transactions(idempotency_key);
