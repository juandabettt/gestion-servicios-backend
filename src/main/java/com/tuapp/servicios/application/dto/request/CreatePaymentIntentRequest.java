package com.tuapp.servicios.application.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.UUID;

@Data
public class CreatePaymentIntentRequest {
    @NotNull
    private UUID invoiceId;
}
