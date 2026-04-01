package com.tuapp.servicios.application.dto.request;

import com.tuapp.servicios.domain.enums.MetodoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Preferencias del usuario")
public class UpdatePreferencesRequest {
    @Min(1) @Max(30)
    @Schema(description = "Días de anticipación para alertas de vencimiento", defaultValue = "5")
    private Integer diasAnticipacionAlerta;

    @Schema(description = "Método de pago por defecto")
    private MetodoPago metodoPagoDefault;

    @DecimalMin("0")
    @Schema(description = "Presupuesto mensual agua (COP)")
    private BigDecimal presupuestoMensualAgua;

    @DecimalMin("0")
    @Schema(description = "Presupuesto mensual energía (COP)")
    private BigDecimal presupuestoMensualEnergia;

    @DecimalMin("0")
    @Schema(description = "Presupuesto mensual gas (COP)")
    private BigDecimal presupuestoMensualGas;

    @DecimalMin("0")
    @Schema(description = "Presupuesto mensual internet (COP)")
    private BigDecimal presupuestoMensualInternet;

    @Schema(description = "Recibir notificaciones por email")
    private Boolean notificacionesEmail;

    @Schema(description = "Recibir notificaciones push")
    private Boolean notificacionesPush;
}
