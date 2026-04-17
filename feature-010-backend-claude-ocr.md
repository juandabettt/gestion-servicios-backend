Eres un asistente de ingeniería de software. Necesito que modifiques exactamente 
2 archivos en mi proyecto Spring Boot para corregir la integración con Claude API 
(Anthropic) en un servicio de OCR de facturas.

## CONTEXTO DEL PROBLEMA

El backend llama a Claude API para analizar imágenes de facturas subidas a Cloudinary.
Actualmente falla con este error:

  "Unable to download the file. Please verify the URL and try again."

Esto ocurre porque Claude intenta descargar la imagen desde la URL de Cloudinary 
directamente, pero no puede acceder a ella. La solución es que el backend descargue 
los bytes de la imagen primero y los envíe a Claude como base64.

Además, el modelo configurado tiene un nombre incorrecto que debe corregirse.

---

## CAMBIO 1 — application.yml

Archivo: src/main/resources/application.yml

Busca esta sección exacta:

  anthropic:
    api-key: ${ANTHROPIC_API_KEY:}
    api-url: https://api.anthropic.com/v1/messages
    model: claude-opus-4-5
    max-tokens: 1024

Cambia SOLO la línea del modelo:

  anthropic:
    api-key: ${ANTHROPIC_API_KEY:}
    api-url: https://api.anthropic.com/v1/messages
    model: claude-opus-4-5-20251101
    max-tokens: 1024

No toques ninguna otra línea de este archivo.

---

## CAMBIO 2 — ClaudeOcrServiceAdapter.java

Archivo: src/main/java/com/tuapp/servicios/infrastructure/adapter/ocr/ClaudeOcrServiceAdapter.java

### 2A — Reemplaza el método extractFromUrl completo

Busca y reemplaza este método completo (desde "private OcrExtractionResult extractFromUrl" 
hasta su llave de cierre):

  private OcrExtractionResult extractFromUrl(String imageUrl) {
      try {
          Map<String, Object> imageSource = Map.of(
                  "type", "url",
                  "url", imageUrl
          );
          return callClaude(imageSource);
      } catch (Exception e) {
          log.error("Error al llamar a Claude API con URL: {}", e.getMessage(), e);
          return emptyResult("Error comunicación con Claude: " + e.getMessage());
      }
  }

Por este nuevo método:

  private OcrExtractionResult extractFromUrl(String imageUrl) {
      try {
          log.info("Descargando imagen desde URL para convertir a base64");

          byte[] imageBytes = downloadImageBytes(imageUrl);

          if (imageBytes == null || imageBytes.length == 0) {
              log.error("No se pudieron descargar los bytes de la imagen desde Cloudinary");
              return emptyResult("No se pudo descargar la imagen desde Cloudinary");
          }

          log.info("Imagen descargada exitosamente: {} bytes. Enviando a Claude como base64.", 
                   imageBytes.length);

          String mimeType = "image/jpeg";
          if (imageUrl.toLowerCase().contains(".png")) {
              mimeType = "image/png";
          } else if (imageUrl.toLowerCase().contains(".webp")) {
              mimeType = "image/webp";
          }

          String base64Image = Base64.getEncoder().encodeToString(imageBytes);

          Map<String, Object> imageSource = Map.of(
                  "type", "base64",
                  "media_type", mimeType,
                  "data", base64Image
          );

          return callClaude(imageSource);

      } catch (Exception e) {
          log.error("Error al procesar imagen desde URL: {}", e.getMessage(), e);
          return emptyResult("Error procesando imagen: " + e.getMessage());
      }
  }

### 2B — Agrega el método downloadImageBytes

Agrega este método nuevo justo DESPUÉS del método extractFromUrl 
(antes del método callClaude):

  private byte[] downloadImageBytes(String imageUrl) {
      try {
          WebClient downloadClient = WebClient.builder().build();

          return downloadClient.get()
                  .uri(imageUrl)
                  .retrieve()
                  .bodyToMono(byte[].class)
                  .block();
      } catch (Exception e) {
          log.error("Error descargando imagen desde URL: {}", e.getMessage());
          return null;
      }
  }

### 2C — Verifica los imports

Confirma que estos imports existen en la parte superior del archivo. 
Si alguno falta, agrégalo:

  import java.util.Base64;
  import org.springframework.web.reactive.function.client.WebClient;

No agregues imports que ya existen. No dupliques ninguno.

---

## RESTRICCIONES IMPORTANTES

- NO modifiques ningún otro archivo fuera de los 2 indicados
- NO cambies ningún otro método en ClaudeOcrServiceAdapter.java
- NO cambies la lógica de callClaude(), parseClaudeResponse(), ni emptyResult()
- NO modifiques el método extractInvoiceData() principal
- NO toques application-production.yml ni ningún otro yml
- Si un import ya existe, no lo dupliques

---

## VERIFICACIÓN ESPERADA

Después de tus cambios, cuando se suba una factura, los logs de Railway 
deben mostrar estas líneas en orden:

  INFO  - Iniciando extracción OCR con Claude via URL pública
  INFO  - Descargando imagen desde URL para convertir a base64
  INFO  - Imagen descargada exitosamente: XXXXX bytes. Enviando a Claude como base64.
  INFO  - OCR completado exitosamente — confianza: XX%

Y el campo estado de la factura debe cambiar de ERROR_OCR a PENDIENTE.

Por favor confirma qué cambios realizaste en cada archivo antes de terminar.