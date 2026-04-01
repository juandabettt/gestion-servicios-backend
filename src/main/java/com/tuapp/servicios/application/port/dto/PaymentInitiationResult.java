package com.tuapp.servicios.application.port.dto;

import com.tuapp.servicios.domain.enums.EstadoTransaccion;
import lombok.Builder;
import lombok.Data;

@Data @Builder
public class PaymentInitiationResult {
    private String gatewayTransactionId;
    private EstadoTransaccion estado;
    private String urlRedireccionPse;
    private String mensaje;
    private boolean exitoso;
}
