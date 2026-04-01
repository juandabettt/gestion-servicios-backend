package com.tuapp.servicios.application.dto.response;

import com.tuapp.servicios.domain.enums.MetodoPago;
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
    private UUID propertyId;
    private String propertyNombre;
    private UUID proveedorId;
    private String proveedorNombre;
    private MetodoPago metodoPago;
    private Integer diasAntesVencimiento;
    private BigDecimal montoMaximo;
    private Boolean activo;
    private LocalDateTime ultimaEjecucion;
    private LocalDateTime createdAt;
}
