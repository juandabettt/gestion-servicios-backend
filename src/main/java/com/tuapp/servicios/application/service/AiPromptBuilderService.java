package com.tuapp.servicios.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.port.dto.ConsumptionHistoryContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiPromptBuilderService {

    private final ObjectMapper objectMapper;

    public String buildAnalysisPrompt(ConsumptionHistoryContext context) {
        String historialJson;
        try {
            historialJson = objectMapper.writeValueAsString(context);
        } catch (JsonProcessingException e) {
            log.error("Error serializando contexto para prompt IA");
            historialJson = "{}";
        }
        return """
                Eres un asesor experto en eficiencia energética y gestión de servicios públicos domésticos en Colombia.
                Tu objetivo es analizar el historial de consumo de un hogar y proporcionar análisis accionable.

                DATOS DEL HOGAR:
                %s

                INSTRUCCIONES:
                1. Analiza el historial de consumo e identifica anomalías (desviaciones >20%% del promedio).
                2. Genera recomendaciones específicas y personalizadas de ahorro.
                3. Predice el consumo del próximo mes si hay al menos 3 meses de historial.
                4. Si hay datos de benchmark de vecinos, incluye comparativa.

                Responde ÚNICAMENTE con un JSON válido con esta estructura exacta:
                {
                  "anomalias": [{"descripcion":"","periodo":"","desviacionPorcentual":0,"severidad":"ALTA|MEDIA|BAJA"}],
                  "recomendaciones": [{"titulo":"","descripcion":"","ahorroEstimado":"","prioridad":"ALTA|MEDIA|BAJA"}],
                  "prediccionProximaFactura": {"montoEstimado":0,"consumoEstimado":0,"rangoBajo":0,"rangoAlto":0,"factores":[]},
                  "resumenEjecutivo": ""
                }
                """.formatted(historialJson);
    }
}
