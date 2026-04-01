package com.tuapp.servicios.application.port.dto;

import com.tuapp.servicios.domain.enums.MetodoPago;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder
public class PaymentRequest {
    private UUID invoiceId;
    private BigDecimal amount;
    private MetodoPago metodoPago;
    private String bancoOrigen;
    private String idempotencyKey;
    private String codigoConvenioRecaudo;
    private String numeroReferencia;
}
