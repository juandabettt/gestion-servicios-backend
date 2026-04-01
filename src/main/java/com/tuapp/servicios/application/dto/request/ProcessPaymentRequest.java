package com.tuapp.servicios.application.dto.request;

import com.tuapp.servicios.domain.enums.MetodoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.UUID;

@Data
@Schema(description = "Solicitud de pago de factura")
public class ProcessPaymentRequest {
    @NotNull
    @Schema(description = "ID de la factura a pagar")
    private UUID invoiceId;

    @NotNull
    @Schema(description = "Método de pago")
    private MetodoPago metodoPago;

    @Size(max = 100)
    @Schema(description = "Banco origen (requerido para PSE)", example = "Bancolombia")
    private String bancoOrigen;

    @NotBlank @Size(max = 255)
    @Schema(description = "Clave de idempotencia única para este pago")
    private String idempotencyKey;
}
