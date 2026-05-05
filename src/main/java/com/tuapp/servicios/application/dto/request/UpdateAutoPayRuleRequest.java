package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Schema(description = "Actualización de regla de autopago")
public class UpdateAutoPayRuleRequest {

    @Size(max = 100)
    @Schema(description = "Nuevo nombre de la regla")
    private String nombre;

    @Pattern(regexp = "ENERGIA|AGUA|GAS|INTERNET|TELEFONIA|TODOS")
    @Schema(description = "Tipo de servicio")
    private String tipoServicio;

    @Min(1) @Max(30)
    @Schema(description = "Días antes del vencimiento")
    private Integer diasAntesVencimiento;

    @Schema(description = "Nuevo monto máximo (null para eliminar el límite)")
    private BigDecimal montoMaximo;

    @Schema(description = "Activar o desactivar la regla")
    private Boolean activa;
}
