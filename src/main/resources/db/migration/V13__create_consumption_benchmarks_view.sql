-- Vista materializada para comparativa de consumo vs. hogares similares
-- Requiere minimo 5 hogares distintos para garantizar anonimato estadistico
CREATE MATERIALIZED VIEW IF NOT EXISTS consumption_benchmarks AS
SELECT
    p.ciudad,
    i.proveedor_id,
    DATE_TRUNC('month', i.fecha_emision) AS periodo,
    AVG(i.consumo_unidad) AS consumo_promedio,
    PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY i.consumo_unidad) AS percentil_25,
    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY i.consumo_unidad) AS percentil_75,
    COUNT(DISTINCT p.user_id) AS numero_hogares
FROM invoices i
JOIN properties p ON i.property_id = p.id
WHERE i.deleted_at IS NULL
  AND p.deleted_at IS NULL
  AND i.consumo_unidad IS NOT NULL
  AND i.estado = 'PAGADA'
GROUP BY p.ciudad, i.proveedor_id, DATE_TRUNC('month', i.fecha_emision)
HAVING COUNT(DISTINCT p.user_id) >= 5;

-- Indice para acelerar consultas de benchmark
CREATE UNIQUE INDEX IF NOT EXISTS idx_consumption_benchmarks_unique
    ON consumption_benchmarks(ciudad, proveedor_id, periodo);
