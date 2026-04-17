Necesito hacer 2 cambios en mi proyecto Spring Boot para 
solucionar rate limiting y reducir costos de Claude API.

## CAMBIO 1 — application.yml
Archivo: src/main/resources/application.yml

Busca esta línea exacta:
  model: claude-opus-4-5-20251101

Reemplázala por:
  model: claude-haiku-4-5-20251001

## CAMBIO 2 — RateLimitingFilter.java
Archivo: src/main/java/com/tuapp/servicios/web/filter/RateLimitingFilter.java

Busca este bloque exacto:
  } else if (path.contains("/invoices/upload")) {
      limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1)));

Reemplázalo por:
  } else if (path.contains("/invoices/upload")) {
      limit = Bandwidth.classic(50, Refill.intervally(50, Duration.ofHours(1)));

## RESTRICCIONES
- Modifica SOLO los 2 archivos indicados
- SOLO cambia las líneas exactas mencionadas
- No toques ningún otro método ni archivo
- Confirma qué líneas cambiaste al terminar
