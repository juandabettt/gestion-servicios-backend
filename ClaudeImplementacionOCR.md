Necesito corregir el método downloadImageBytes en ClaudeOcrServiceAdapter.java.

El error actual es:
  "Error descargando imagen desde URL: 200 OK from GET https://res.cloudinary.com/..."

La respuesta HTTP es 200 OK (la imagen existe) pero WebClient falla al 
convertir la respuesta a byte[]. Necesito reemplazar el método con una 
implementación más robusta usando RestTemplate en lugar de WebClient.

## CAMBIO ÚNICO — ClaudeOcrServiceAdapter.java

Archivo: src/main/java/com/tuapp/servicios/infrastructure/adapter/ocr/ClaudeOcrServiceAdapter.java

### Paso 1 — Reemplaza el método downloadImageBytes completo

Busca este método exacto:

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

Reemplázalo por esto:

    private byte[] downloadImageBytes(String imageUrl) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    imageUrl,
                    HttpMethod.GET,
                    null,
                    byte[].class
            );
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody();
            }
            log.error("Respuesta inesperada al descargar imagen: {}", response.getStatusCode());
            return null;
        } catch (Exception e) {
            log.error("Error descargando imagen desde URL: {}", e.getMessage());
            return null;
        }
    }

### Paso 2 — Agrega los imports necesarios

Verifica que estos imports existen en la parte superior del archivo.
Si alguno falta, agrégalo. No dupliques los que ya existen:

    import org.springframework.http.HttpMethod;
    import org.springframework.http.ResponseEntity;
    import org.springframework.web.client.RestTemplate;

## RESTRICCIONES
- Modifica SOLO ClaudeOcrServiceAdapter.java
- SOLO reemplaza el método downloadImageBytes
- No toques ningún otro método
- No modifiques extractFromUrl ni callClaude
- Confirma qué líneas cambiaste al terminar
