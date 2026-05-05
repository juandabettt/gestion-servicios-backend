package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Regla de autopago")
public class CreateAutoPayRuleRequest {

    @NotBlank
    @Size(max = 100)
    @Schema(description = "Nombre descriptivo de la regla", example = "Pagar luz automáticamente")
    private String nombre;

    @Pattern(regexp = "ENERGIA|AGUA|GAS|INTERNET|TELEFONIA|TODOS")
    @Schema(description = "Tipo de servicio a cubrir (TODOS para todos)", defaultValue = "TODOS")
    private String tipoServicio = "TODOS";

    @NotNull @Min(1) @Max(30)
    @Schema(description = "Días antes del vencimiento para ejecutar el pago", defaultValue = "3")
    private Integer diasAntesVencimiento = 3;

    @DecimalMin("0.01")
    @Schema(description = "Monto máximo a pagar automáticamente (null = sin límite)")
    private BigDecimal montoMaximo;
}
