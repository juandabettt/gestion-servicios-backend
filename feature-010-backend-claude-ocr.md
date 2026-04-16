# FEATURE-010 BACKEND: Reemplazar Mock OCR por Claude AI (Anthropic)

## Contexto
Actualmente el proyecto usa MockOcrServiceAdapter y MockAiAnalysisAdapter que devuelven datos inventados. Se necesita reemplazarlos por un adaptador real que use la API de Claude (Anthropic) para leer las imágenes de facturas y extraer los datos reales.

## Variable de entorno disponible en Railway
```
ANTHROPIC_API_KEY=<ya está configurada en Railway>
```

## Lo que necesitas hacer

### 1. Agregar dependencia HTTP en pom.xml

Verifica si ya existe una dependencia para hacer llamadas HTTP como `spring-boot-starter-webflux` o `okhttp`. Si no existe ninguna, agrega en pom.xml:

```xml
<!-- Cliente HTTP para llamadas a Anthropic API -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### 2. Agregar configuración en application.yml

En `src/main/resources/application.yml` agrega:

```yaml
anthropic:
  api-key: ${ANTHROPIC_API_KEY:}
  api-url: https://api.anthropic.com/v1/messages
  model: claude-opus-4-5
  max-tokens: 1024
```

### 3. Crear el adaptador real de Claude

Crea el archivo:
`src/main/java/com/tuapp/servicios/infrastructure/adapter/ai/ClaudeOcrServiceAdapter.java`

```java
package com.tuapp.servicios.infrastructure.adapter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.port.out.OcrServicePort;
import com.tuapp.servicios.domain.model.OcrResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile("production")
@RequiredArgsConstructor
public class ClaudeOcrServiceAdapter implements OcrServicePort {

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${anthropic.api-url}")
    private String apiUrl;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.max-tokens}")
    private int maxTokens;

    private final ObjectMapper objectMapper;

    private static final String PROMPT = """
        Eres un asistente especializado en leer facturas de servicios públicos colombianos.
        Analiza esta imagen de factura y extrae la siguiente información en formato JSON.
        
        IMPORTANTE: 
        - Si no puedes leer un campo claramente, usa null
        - Los montos deben ser números sin puntos ni comas (ejemplo: 150000)
        - Las fechas deben estar en formato YYYY-MM-DD
        - El tipo de servicio debe ser uno de: ENERGIA, AGUA, GAS, INTERNET, TELEFONO, OTRO
        - Responde SOLO con el JSON, sin texto adicional, sin markdown, sin explicaciones
        
        Formato de respuesta:
        {
          "proveedor": "nombre de la empresa que emite la factura",
          "tipoServicio": "ENERGIA|AGUA|GAS|INTERNET|TELEFONO|OTRO",
          "numeroFactura": "número o referencia de la factura",
          "fechaEmision": "YYYY-MM-DD",
          "fechaVencimiento": "YYYY-MM-DD",
          "periodoFacturado": "periodo que cubre la factura ej: 2025-03",
          "montoTotal": 150000,
          "consumoUnidad": 284,
          "unidadMedida": "kWh|m3|minutos|etc",
          "confianza": 85
        }
        """;

    @Override
    public OcrResult extractInvoiceData(String imageUrl) {
        log.info("Iniciando extracción OCR con Claude para imagen: {}", imageUrl);

        try {
            WebClient client = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();

            Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", List.of(
                    Map.of(
                        "role", "user",
                        "content", List.of(
                            Map.of(
                                "type", "image",
                                "source", Map.of(
                                    "type", "url",
                                    "url", imageUrl
                                )
                            ),
                            Map.of(
                                "type", "text",
                                "text", PROMPT
                            )
                        )
                    )
                )
            );

            String response = client.post()
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();

            return parseClaudeResponse(response);

        } catch (Exception e) {
            log.error("Error al llamar a Claude API: {}", e.getMessage(), e);
            return OcrResult.empty();
        }
    }

    private OcrResult parseClaudeResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("content").get(0).path("text").asText();

            // Limpiar posible markdown
            content = content.replaceAll("```json", "").replaceAll("```", "").trim();

            JsonNode data = objectMapper.readTree(content);

            return OcrResult.builder()
                .proveedor(getTextOrNull(data, "proveedor"))
                .tipoServicio(getTextOrNull(data, "tipoServicio"))
                .numeroFactura(getTextOrNull(data, "numeroFactura"))
                .fechaEmision(parseDate(getTextOrNull(data, "fechaEmision")))
                .fechaVencimiento(parseDate(getTextOrNull(data, "fechaVencimiento")))
                .periodoFacturado(getTextOrNull(data, "periodoFacturado"))
                .montoTotal(getBigDecimalOrNull(data, "montoTotal"))
                .consumoUnidad(getBigDecimalOrNull(data, "consumoUnidad"))
                .unidadMedida(getTextOrNull(data, "unidadMedida"))
                .confianza(data.has("confianza") ? data.get("confianza").doubleValue() : 0.0)
                .exitoso(true)
                .build();

        } catch (Exception e) {
            log.error("Error al parsear respuesta de Claude: {}", e.getMessage(), e);
            return OcrResult.empty();
        }
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        String text = value.asText().trim();
        return text.isEmpty() || text.equals("null") ? null : text;
    }

    private BigDecimal getBigDecimalOrNull(JsonNode node, String field) {
        try {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) return null;
            return new BigDecimal(value.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            log.warn("No se pudo parsear la fecha: {}", dateStr);
            return null;
        }
    }
}
```

### 4. Verificar la interfaz OcrServicePort y OcrResult

Busca `OcrServicePort.java` y verifica que tenga el método:
```java
OcrResult extractInvoiceData(String imageUrl);
```

Busca `OcrResult.java` y verifica que tenga todos los campos usados en el adaptador:
- proveedor, tipoServicio, numeroFactura
- fechaEmision, fechaVencimiento, periodoFacturado
- montoTotal, consumoUnidad, unidadMedida
- confianza, exitoso

Si faltan campos en `OcrResult`, agrégalos con Lombok `@Builder` y `@Data`.

Verifica que tenga el método estático `empty()`:
```java
public static OcrResult empty() {
    return OcrResult.builder()
        .exitoso(false)
        .confianza(0.0)
        .build();
}
```

### 5. Desactivar el Mock en producción

Busca `MockOcrServiceAdapter.java` y verifica que tenga `@Profile("local")` o similar. Si tiene `@Profile({"local", "production"})`, cambia a solo `@Profile("local")` para que no se active en producción:

```java
@Profile("local")  // Solo activo en local, NO en production
@Component
public class MockOcrServiceAdapter implements OcrServicePort {
```

Haz lo mismo con `MockAiAnalysisAdapter.java`:
```java
@Profile("local")
@Component
public class MockAiAnalysisAdapter implements AiAnalysisPort {
```

### 6. Validar que la imagen es una factura

Dentro de `ClaudeOcrServiceAdapter`, modifica el PROMPT para que Claude indique si la imagen NO es una factura:

El prompt ya incluye esta lógica — si Claude no puede leer datos válidos de factura, devuelve nulls y `confianza: 0`, lo que el servicio puede usar para rechazar la imagen.

En el servicio que procesa el resultado del OCR (`InvoiceService` o similar), agrega validación:

```java
if (ocrResult.getConfianza() < 30 && ocrResult.getMontoTotal() == null) {
    throw new BusinessException("La imagen no parece ser una factura válida. Por favor sube una foto clara de tu factura.");
}
```

## Archivos a modificar
- `pom.xml` — agregar webflux si no existe
- `application.yml` — agregar configuración anthropic
- `ClaudeOcrServiceAdapter.java` — CREAR nuevo
- `MockOcrServiceAdapter.java` — cambiar @Profile a solo "local"
- `MockAiAnalysisAdapter.java` — cambiar @Profile a solo "local"
- `OcrResult.java` — agregar campos faltantes si los hay
- `InvoiceServiceImpl.java` — agregar validación de confianza

## NO modificar
- Lógica de upload de imágenes a Cloudinary
- Endpoints del controller
- Migraciones de base de datos
- Lógica de pagos
- Frontend