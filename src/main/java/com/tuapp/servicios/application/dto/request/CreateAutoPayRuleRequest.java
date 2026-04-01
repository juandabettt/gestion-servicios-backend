package com.tuapp.servicios.application.dto.request;

import com.tuapp.servicios.domain.enums.MetodoPago;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.UUID;

@Data
@Schema(description = "Regla de autopago")
public class CreateAutoPayRuleRequest {
    @NotNull
    @Schema(description = "ID de la propiedad")
    private UUID propertyId;

    @NotNull
    @Schema(description = "ID del proveedor")
    private UUID proveedorId;

    @NotNull
    @Schema(description = "Método de pago")
    private MetodoPago metodoPago;

    @NotNull @Min(1) @Max(30)
    @Schema(description = "Días antes del vencimiento para ejecutar el pago", defaultValue = "2")
    private Integer diasAntesVencimiento = 2;

    @DecimalMin("0.01")
    @Schema(description = "Monto máximo a pagar automáticamente (protección)")
    private BigDecimal montoMaximo;
}
