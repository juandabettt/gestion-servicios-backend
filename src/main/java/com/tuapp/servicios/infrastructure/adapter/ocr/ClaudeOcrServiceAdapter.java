package com.tuapp.servicios.infrastructure.adapter.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.port.OcrServicePort;
import com.tuapp.servicios.application.port.dto.OcrExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
        Analiza esta imagen de una factura de servicio público colombiano
        y extrae los siguientes datos con precisión.

        INSTRUCCIONES IMPORTANTES:
        - Para el PROVEEDOR: busca el nombre de la empresa en el logo o
          encabezado principal. Ejemplos: CEDENAR, EMPOPASTO, ALCANOS,
          CLARO, ETB, MOVISTAR, EPM, CODENSA. Devuelve SOLO el nombre
          sin texto adicional.
        - Para el PERÍODO: busca "MES FACTURADO" o "PERÍODO FACTURADO".
          Devuelve en formato YYYY-MM (ejemplo: 2026-03 para MARZO/2026).
        - Para la FECHA DE VENCIMIENTO: busca "PAGO OPORTUNO", "FECHA
          LÍMITE DE PAGO" o "VENCE". Formato: YYYY-MM-DD.
        - Para el MONTO: busca "TOTAL A PAGAR", "VALOR A PAGAR" o
          "VALOR FACTURA". Solo el número sin símbolos ni puntos de miles.
        - Para el CONSUMO: busca en la sección "DATOS DEL CONSUMO" o
          "CONSUMO DEL PERÍODO" el valor numérico del consumo real
          del período actual. Para energía son kWh, para agua son m³.
        - Para el TIPO DE SERVICIO: determina si es
          ENERGIA, AGUA, GAS o INTERNET basándote en el contenido.

        Responde ÚNICAMENTE con este JSON sin texto adicional:
        {
          "empresa": "nombre exacto del proveedor",
          "tipoServicio": "ENERGIA|AGUA|GAS|INTERNET",
          "periodoFacturado": "YYYY-MM",
          "fechaVencimiento": "YYYY-MM-DD",
          "montoTotal": numero_sin_puntos_ni_comas,
          "consumoUnidad": numero_decimal,
          "unidadMedida": "kWh|m3|GB",
          "numeroReferencia": "referencia o consecutivo de pago",
          "confianza": numero_entre_0_y_100
        }

        Si no encuentras algún dato, usa null para ese campo.
        NO incluyas texto antes o después del JSON.
        """;

    @Override
    public OcrExtractionResult extractInvoiceData(byte[] imageBytes, String mimeType) {
        if ("url".equals(mimeType)) {
            String imageUrl = new String(imageBytes, StandardCharsets.UTF_8);
            log.info("Iniciando extracción OCR con Claude via URL pública");
            return extractFromUrl(imageUrl);
        }

        log.info("Iniciando extracción OCR con Claude para imagen de {} bytes", imageBytes.length);

        if (imageBytes == null || imageBytes.length == 0) {
            log.warn("Imagen vacía recibida, retornando resultado vacío");
            return emptyResult("Imagen vacía");
        }

        try {
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            Map<String, Object> imageSource = Map.of(
                    "type", "base64",
                    "media_type", mimeType,
                    "data", base64Image
            );

            return callClaude(imageSource);

        } catch (Exception e) {
            log.error("Error al llamar a Claude API: {}", e.getMessage(), e);
            return emptyResult("Error comunicación con Claude: " + e.getMessage());
        }
    }

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

    private OcrExtractionResult callClaude(Map<String, Object> imageSource) {
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
                                                "source", imageSource
                                        ),
                                        Map.of(
                                                "type", "text",
                                                "text", PROMPT
                                        )
                                )
                        )
                )
        );

        try {
            log.info("Claude API request body: {}", objectMapper.writeValueAsString(requestBody));
        } catch (Exception e) {
            log.warn("No se pudo serializar el request body para log: {}", e.getMessage());
        }

        String response = client.post()
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class).map(errorBody -> {
                            log.error("Claude API error {} — body: {}", clientResponse.statusCode(), errorBody);
                            return new RuntimeException("Claude API error " + clientResponse.statusCode() + ": " + errorBody);
                        }))
                .bodyToMono(String.class)
                .block();

        return parseClaudeResponse(response);
    }

    private OcrExtractionResult parseClaudeResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("content").get(0).path("text").asText();

            content = content.replaceAll("```json", "").replaceAll("```", "").trim();

            JsonNode data = objectMapper.readTree(content);

            BigDecimal confianza = getBigDecimalOrNull(data, "confianza");

            return OcrExtractionResult.builder()
                    .empresa(getTextOrNull(data, "empresa"))
                    .tipoServicio(getTextOrNull(data, "tipoServicio"))
                    .numeroReferencia(getTextOrNull(data, "numeroReferencia"))
                    .fechaVencimiento(parseDate(getTextOrNull(data, "fechaVencimiento")))
                    .periodoFacturado(getTextOrNull(data, "periodoFacturado"))
                    .montoTotal(getBigDecimalOrNull(data, "montoTotal"))
                    .consumoUnidad(getBigDecimalOrNull(data, "consumoUnidad"))
                    .unidadMedida(getTextOrNull(data, "unidadMedida"))
                    .confianza(confianza != null ? confianza : BigDecimal.ZERO)
                    .datosRaw(response)
                    .exitoso(true)
                    .build();

        } catch (Exception e) {
            log.error("Error al parsear respuesta de Claude: {}", e.getMessage(), e);
            return emptyResult("Error parseando respuesta: " + e.getMessage());
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

    private OcrExtractionResult emptyResult(String errorMensaje) {
        return OcrExtractionResult.builder()
                .exitoso(false)
                .confianza(BigDecimal.ZERO)
                .errorMensaje(errorMensaje)
                .build();
    }
}
