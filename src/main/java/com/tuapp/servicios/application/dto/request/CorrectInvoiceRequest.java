package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "Corrección manual de datos OCR de una factura")
public class CorrectInvoiceRequest {
    @Schema(description = "Número de referencia de la factura")
    private String numeroReferencia;

    @Schema(description = "Fecha de emisión")
    private LocalDate fechaEmision;

    @Schema(description = "Fecha de vencimiento")
    private LocalDate fechaVencimiento;

    @DecimalMin("0.0001")
    @Schema(description = "Monto total")
    private BigDecimal montoTotal;

    @DecimalMin("0")
    @Schema(description = "Consumo en unidades")
    private BigDecimal consumoUnidad;

    @Size(max = 20)
    @Schema(description = "Unidad de medida", example = "kWh")
    private String unidadMedida;

    @Size(max = 20)
    @Schema(description = "Periodo facturado", example = "2025-01")
    private String periodoFacturado;
}
