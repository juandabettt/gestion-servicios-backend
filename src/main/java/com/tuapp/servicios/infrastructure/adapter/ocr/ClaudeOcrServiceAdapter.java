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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
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
        Eres un asistente especializado en leer facturas de servicios públicos colombianos.
        Analiza esta imagen de factura y extrae la siguiente información en formato JSON.

        IMPORTANTE:
        - Si no puedes leer un campo claramente, usa null
        - Los montos deben ser números sin puntos ni comas (ejemplo: 150000)
        - Las fechas deben estar en formato YYYY-MM-DD
        - Responde SOLO con el JSON, sin texto adicional, sin markdown, sin explicaciones

        Formato de respuesta:
        {
          "empresa": "nombre de la empresa que emite la factura",
          "numeroReferencia": "número o referencia de la factura",
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

        String response = client.post()
                .bodyValue(requestBody)
                .retrieve()
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
                    .numeroReferencia(getTextOrNull(data, "numeroReferencia"))
                    .fechaEmision(parseDate(getTextOrNull(data, "fechaEmision")))
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
