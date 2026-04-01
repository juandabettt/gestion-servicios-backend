package com.tuapp.servicios.application.dto.response;

import com.tuapp.servicios.domain.enums.EstadoTransaccion;
import com.tuapp.servicios.domain.enums.MetodoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
@Schema(description = "Transacción de pago")
public class PaymentTransactionResponse {
    private UUID id;
    private UUID facturaId;
    private BigDecimal montoTransaccion;
    private MetodoPago metodoPago;
    private EstadoTransaccion estadoTransaccion;
    @Schema(description = "URL bancaria para redirección PSE")
    private String urlRedireccionPse;
    private LocalDateTime fechaConfirmacion;
    private LocalDateTime createdAt;
}
