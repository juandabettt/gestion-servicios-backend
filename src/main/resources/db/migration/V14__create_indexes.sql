-- Indices adicionales de rendimiento

-- Busqueda de facturas vencidas por fecha
CREATE INDEX IF NOT EXISTS idx_invoices_fecha_vencimiento
    ON invoices(fecha_vencimiento) WHERE estado = 'PENDIENTE' AND deleted_at IS NULL;

-- Jobs por tipo para monitoreo
CREATE INDEX IF NOT EXISTS idx_job_queue_tipo_job ON job_queue(tipo_job, estado);

-- Busqueda de transacciones por pasarela (para webhook)
CREATE INDEX IF NOT EXISTS idx_payment_transactions_pasarela
    ON payment_transactions(id_transaccion_pasarela) WHERE deleted_at IS NULL;

-- Analisis IA por fecha de creacion para consultas recientes
CREATE INDEX IF NOT EXISTS idx_ai_analysis_created_at ON ai_analysis(created_at DESC) WHERE deleted_at IS NULL;

-- Notificaciones pendientes de envio
CREATE INDEX IF NOT EXISTS idx_notification_logs_pendientes
    ON notification_logs(estado, intentos, created_at) WHERE estado = 'PENDIENTE';

-- Funcion para refrescar la vista materializada (llamar nightly)
CREATE OR REPLACE FUNCTION refresh_consumption_benchmarks()
RETURNS void AS $$
BEGIN
    REFRESH MATERIALIZED VIEW CONCURRENTLY consumption_benchmarks;
END;
$$ LANGUAGE plpgsql;
