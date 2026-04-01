package com.tuapp.servicios.infrastructure.adapter.ai;

import com.tuapp.servicios.application.port.AiAnalysisPort;
import com.tuapp.servicios.application.port.dto.AiAnalysisPortResult;
import com.tuapp.servicios.application.port.dto.ConsumptionHistoryContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@Profile("local")
@Slf4j
public class MockAiAnalysisAdapter implements AiAnalysisPort {

    @Override
    public AiAnalysisPortResult analyzeConsumption(ConsumptionHistoryContext context) {
        log.info("Mock AI: analizando historial de {} para servicio {}",
                context.getPropertyId(), context.getTipoServicio());

        return AiAnalysisPortResult.builder()
                .anomalias(List.of(
                    AiAnalysisPortResult.AnomalyDetection.builder()
                        .descripcion("Consumo atípicamente alto en enero: 45% sobre el promedio histórico")
                        .periodo("2025-01")
                        .desviacionPorcentual(BigDecimal.valueOf(45.0))
                        .severidad("ALTA")
                        .build()
                ))
                .recomendaciones(List.of(
                    AiAnalysisPortResult.Recommendation.builder()
                        .titulo("Verificar electrodomésticos de alto consumo")
                        .descripcion("El calentador de agua podría estar fallando. Una revisión técnica puede reducir el consumo en un 20%.")
                        .ahorroEstimado("Ahorro estimado: $35.000/mes")
                        .prioridad("ALTA")
                        .build(),
                    AiAnalysisPortResult.Recommendation.builder()
                        .titulo("Cambiar a tarifa nocturna")
                        .descripcion("Usa electrodomésticos de alto consumo después de las 10pm para aprovechar la tarifa reducida.")
                        .ahorroEstimado("Ahorro estimado: $15.000/mes")
                        .prioridad("MEDIA")
                        .build()
                ))
                .prediccionProximaFactura(AiAnalysisPortResult.ConsumptionPrediction.builder()
                        .montoEstimado(BigDecimal.valueOf(185000))
                        .consumoEstimado(BigDecimal.valueOf(220))
                        .rangoBajo(BigDecimal.valueOf(160000))
                        .rangoAlto(BigDecimal.valueOf(210000))
                        .factores(List.of(
                            "Tendencia decreciente en los últimos 2 meses",
                            "Época de lluvia reduce consumo de calentamiento"
                        ))
                        .build())
                .resumenEjecutivo("Tu consumo de energía se encuentra 15% por encima del promedio de hogares similares en tu ciudad. " +
                        "Hemos identificado 1 anomalía y 2 oportunidades de ahorro que pueden reducir tu factura mensual en hasta $50.000.")
                .exitoso(true)
                .build();
    }
}
