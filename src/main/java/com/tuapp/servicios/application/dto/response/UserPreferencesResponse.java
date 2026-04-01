package com.tuapp.servicios.application.dto.response;

import com.tuapp.servicios.domain.enums.MetodoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder
@Schema(description = "Preferencias del usuario")
public class UserPreferencesResponse {
    private UUID id;
    private Integer diasAnticipacionAlerta;
    private MetodoPago metodoPagoDefault;
    private BigDecimal presupuestoMensualAgua;
    private BigDecimal presupuestoMensualEnergia;
    private BigDecimal presupuestoMensualGas;
    private BigDecimal presupuestoMensualInternet;
    private Boolean notificacionesEmail;
    private Boolean notificacionesPush;
    private String moneda;
}
