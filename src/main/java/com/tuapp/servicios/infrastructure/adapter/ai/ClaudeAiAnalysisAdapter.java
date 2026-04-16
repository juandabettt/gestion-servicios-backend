package com.tuapp.servicios.infrastructure.adapter.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.port.AiAnalysisPort;
import com.tuapp.servicios.application.port.dto.AiAnalysisPortResult;
import com.tuapp.servicios.application.port.dto.ConsumptionHistoryContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Profile("production")
@RequiredArgsConstructor
public class ClaudeAiAnalysisAdapter implements AiAnalysisPort {

    @Value("${anthropic.api-key}")
    private String apiKey;

    @Value("${anthropic.api-url}")
    private String apiUrl;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.max-tokens}")
    private int maxTokens;

    private final ObjectMapper objectMapper;

    private static final String PROMPT_TEMPLATE = """
        Eres un asistente especializado en análisis de consumo de servicios públicos colombianos.
        Analiza el siguiente historial de consumo y responde SOLO con JSON, sin markdown ni explicaciones.

        Datos:
        - Propiedad: %s
        - Tipo de servicio: %s
        - Historial de facturas: %s

        Formato de respuesta:
        {
          "anomalias": [
            {
              "descripcion": "descripción de la anomalía",
              "periodo": "YYYY-MM",
              "desviacionPorcentual": 45.0,
              "severidad": "ALTA|MEDIA|BAJA"
            }
          ],
          "recomendaciones": [
            {
              "titulo": "título corto",
              "descripcion": "descripción de la recomendación",
              "ahorroEstimado": "Ahorro estimado: $XX.000/mes",
              "prioridad": "ALTA|MEDIA|BAJA"
            }
          ],
          "prediccionProximaFactura": {
            "montoEstimado": 180000,
            "consumoEstimado": 210,
            "rangoBajo": 160000,
            "rangoAlto": 200000,
            "factores": ["factor 1", "factor 2"]
          },
          "resumenEjecutivo": "resumen en 2-3 oraciones"
        }
        """;

    @Override
    public AiAnalysisPortResult analyzeConsumption(ConsumptionHistoryContext context) {
        log.info("Iniciando análisis AI con Claude para propiedad {} - servicio {}",
                context.getPropertyId(), context.getTipoServicio());

        try {
            String historialJson = objectMapper.writeValueAsString(context.getHistorial());
            String prompt = String.format(PROMPT_TEMPLATE,
                    context.getPropertyId(),
                    context.getTipoServicio() != null ? context.getTipoServicio().name() : "DESCONOCIDO",
                    historialJson);

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
                            Map.of("role", "user", "content", prompt)
                    )
            );

            String response = client.post()
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return parseClaudeResponse(response);

        } catch (Exception e) {
            log.error("Error al llamar a Claude API para análisis: {}", e.getMessage(), e);
            return errorResult("Error comunicación con Claude: " + e.getMessage());
        }
    }

    private AiAnalysisPortResult parseClaudeResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            String content = root.path("content").get(0).path("text").asText();
            content = content.replaceAll("```json", "").replaceAll("```", "").trim();

            JsonNode data = objectMapper.readTree(content);

            List<AiAnalysisPortResult.AnomalyDetection> anomalias = parseAnomalias(data.path("anomalias"));
            List<AiAnalysisPortResult.Recommendation> recomendaciones = parseRecomendaciones(data.path("recomendaciones"));
            AiAnalysisPortResult.ConsumptionPrediction prediccion = parsePrediccion(data.path("prediccionProximaFactura"));
            String resumen = data.has("resumenEjecutivo") ? data.get("resumenEjecutivo").asText() : "";

            return AiAnalysisPortResult.builder()
                    .anomalias(anomalias)
                    .recomendaciones(recomendaciones)
                    .prediccionProximaFactura(prediccion)
                    .resumenEjecutivo(resumen)
                    .exitoso(true)
                    .build();

        } catch (Exception e) {
            log.error("Error al parsear respuesta de Claude (análisis): {}", e.getMessage(), e);
            return errorResult("Error parseando respuesta: " + e.getMessage());
        }
    }

    private List<AiAnalysisPortResult.AnomalyDetection> parseAnomalias(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) return Collections.emptyList();
        try {
            return objectMapper.readerForListOf(AiAnalysisPortResult.AnomalyDetection.class).readValue(node);
        } catch (Exception e) {
            log.warn("No se pudieron parsear anomalias: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<AiAnalysisPortResult.Recommendation> parseRecomendaciones(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) return Collections.emptyList();
        try {
            return objectMapper.readerForListOf(AiAnalysisPortResult.Recommendation.class).readValue(node);
        } catch (Exception e) {
            log.warn("No se pudieron parsear recomendaciones: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private AiAnalysisPortResult.ConsumptionPrediction parsePrediccion(JsonNode node) {
        if (node == null || node.isMissingNode()) return null;
        try {
            return AiAnalysisPortResult.ConsumptionPrediction.builder()
                    .montoEstimado(getBigDecimal(node, "montoEstimado"))
                    .consumoEstimado(getBigDecimal(node, "consumoEstimado"))
                    .rangoBajo(getBigDecimal(node, "rangoBajo"))
                    .rangoAlto(getBigDecimal(node, "rangoAlto"))
                    .factores(parseFactores(node.path("factores")))
                    .build();
        } catch (Exception e) {
            log.warn("No se pudo parsear prediccion: {}", e.getMessage());
            return null;
        }
    }

    private List<String> parseFactores(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) return Collections.emptyList();
        try {
            return objectMapper.readerForListOf(String.class).readValue(node);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private BigDecimal getBigDecimal(JsonNode node, String field) {
        try {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) return null;
            return new BigDecimal(value.asText().trim());
        } catch (Exception e) {
            return null;
        }
    }

    private AiAnalysisPortResult errorResult(String mensaje) {
        return AiAnalysisPortResult.builder()
                .exitoso(false)
                .errorMensaje(mensaje)
                .anomalias(Collections.emptyList())
                .recomendaciones(Collections.emptyList())
                .build();
    }
}
