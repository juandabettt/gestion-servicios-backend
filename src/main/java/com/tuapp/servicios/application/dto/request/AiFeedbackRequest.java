package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Calificación del usuario sobre un análisis IA")
public class AiFeedbackRequest {
    @NotNull @Min(1) @Max(5)
    @Schema(description = "Calificación de 1 a 5", example = "4")
    private Integer calificacion;
}
