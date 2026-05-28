package com.tuapp.servicios.application.dto.response;

import com.tuapp.servicios.application.port.dto.AiAnalysisPortResult;
import com.tuapp.servicios.application.port.dto.ConsumptionHistoryContext;
import com.tuapp.servicios.domain.enums.EstadoAnalisis;
import com.tuapp.servicios.domain.enums.TipoAnalisis;
import com.tuapp.servicios.domain.enums.TipoServicio;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder
@Schema(description = "Análisis de inteligencia artificial")
public class AiAnalysisResponse {
    private UUID id;
    private UUID propertyId;
    private TipoAnalisis tipoAnalisis;
    private TipoServicio tipoServicio;
    private String descripcion;
    private String impactoEstimado;
    private String periodoAnalizado;
    private EstadoAnalisis estado;
    private Integer calificacionUsuario;
    private LocalDateTime createdAt;

    @Schema(description = "Historial de consumo mensual usado en el análisis")
    private List<ConsumptionHistoryContext.ConsumoMensual> consumoHistorico;

    @Schema(description = "Recomendaciones de ahorro detectadas por la IA")
    private List<AiAnalysisPortResult.Recommendation> recomendaciones;

    @Schema(description = "Anomalías de consumo detectadas por la IA")
    private List<AiAnalysisPortResult.AnomalyDetection> anomalias;

    @Schema(description = "Predicción de la próxima factura")
    private AiAnalysisPortResult.ConsumptionPrediction prediccion;
}
