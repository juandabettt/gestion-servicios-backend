package com.tuapp.servicios.application.port.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data @Builder
public class RefundResult {
    private String refundId;
    private BigDecimal montoReembolsado;
    private boolean exitoso;
    private String mensaje;
}
