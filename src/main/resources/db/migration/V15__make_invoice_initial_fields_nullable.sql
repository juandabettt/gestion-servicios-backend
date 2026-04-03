-- Permite guardar una factura en estado PROCESANDO_OCR antes de que el OCR
-- complete y rellene los datos del proveedor y los campos financieros.
ALTER TABLE invoices
    ALTER COLUMN proveedor_id     DROP NOT NULL,
    ALTER COLUMN numero_referencia DROP NOT NULL,
    ALTER COLUMN fecha_emision    DROP NOT NULL,
    ALTER COLUMN fecha_vencimiento DROP NOT NULL,
    ALTER COLUMN monto_total      DROP NOT NULL;
