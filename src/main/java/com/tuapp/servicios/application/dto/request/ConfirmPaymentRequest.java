package com.tuapp.servicios.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class ConfirmPaymentRequest {
    @NotNull
    private UUID invoiceId;

    @NotNull
    private String paymentIntentId;
}
