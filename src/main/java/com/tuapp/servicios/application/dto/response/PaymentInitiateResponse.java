package com.tuapp.servicios.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
@Schema(description = "Respuesta de inicio de pago")
public class PaymentInitiateResponse {
    private UUID transactionId;
    private String estado;
    @Schema(description = "URL de redirección bancaria (solo PSE)")
    private String urlRedireccion;
    private String message;
}
