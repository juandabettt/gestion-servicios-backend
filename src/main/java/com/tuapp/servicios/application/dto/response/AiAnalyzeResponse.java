package com.tuapp.servicios.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
@Schema(description = "Respuesta de solicitud de análisis IA")
public class AiAnalyzeResponse {
    @Schema(description = "ID del análisis creado")
    private UUID analysisId;
    private String message;
}
