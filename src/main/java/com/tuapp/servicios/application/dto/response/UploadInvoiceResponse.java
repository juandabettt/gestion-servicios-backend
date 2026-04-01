package com.tuapp.servicios.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
@Schema(description = "Respuesta tras subir una factura")
public class UploadInvoiceResponse {
    @Schema(description = "ID asignado a la factura")
    private UUID invoiceId;
    @Schema(description = "Mensaje de estado", example = "Factura recibida. Procesando...")
    private String message;
}
