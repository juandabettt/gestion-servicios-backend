package com.tuapp.servicios.application.port.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data @Builder
public class AiAnalysisPortResult {
    private List<AnomalyDetection> anomalias;
    private List<Recommendation> recomendaciones;
    private ConsumptionPrediction prediccionProximaFactura;
    private String resumenEjecutivo;
    private boolean exitoso;
    private String errorMensaje;

    @Data @Builder
    public static class AnomalyDetection {
        private String descripcion;
        private String periodo;
        private BigDecimal desviacionPorcentual;
        private String severidad;
    }

    @Data @Builder
    public static class Recommendation {
        private String titulo;
        private String descripcion;
        private String ahorroEstimado;
        private String prioridad;
    }

    @Data @Builder
    public static class ConsumptionPrediction {
        private BigDecimal montoEstimado;
        private BigDecimal consumoEstimado;
        private BigDecimal rangoBajo;
        private BigDecimal rangoAlto;
        private List<String> factores;
    }
}
