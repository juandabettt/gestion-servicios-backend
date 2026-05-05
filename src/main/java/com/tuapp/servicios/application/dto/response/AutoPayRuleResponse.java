package com.tuapp.servicios.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
@Schema(description = "Regla de autopago configurada")
public class AutoPayRuleResponse {
    private UUID id;
    private String nombre;
    private String tipoServicio;
    private Integer diasAntesVencimiento;
    private BigDecimal montoMaximo;
    private boolean activa;
    private LocalDateTime ultimaEjecucion;
    private Integer totalPagosRealizados;
    private LocalDateTime createdAt;
}
