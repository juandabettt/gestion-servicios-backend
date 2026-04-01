package com.tuapp.servicios.application.dto.request;

import com.tuapp.servicios.domain.enums.TipoServicio;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Datos para crear un proveedor de servicios")
public class CreateProviderRequest {
    @NotBlank @Size(max = 150)
    @Schema(description = "Nombre del proveedor", example = "Aguas de Pasto")
    private String nombre;

    @NotBlank @Size(max = 20)
    @Schema(description = "NIT del proveedor", example = "891200055-5")
    private String nit;

    @NotNull
    @Schema(description = "Tipo de servicio")
    private TipoServicio tipoServicio;

    @NotBlank @Size(max = 50)
    @Schema(description = "Código convenio recaudo")
    private String codigoConvenioRecaudo;

    @Size(max = 255)
    @Schema(description = "URL del portal del proveedor")
    private String urlPortal;

    @Size(max = 20)
    @Schema(description = "Teléfono de soporte")
    private String telefonoSoporte;

    @Size(max = 100)
    @Schema(description = "Ciudad de cobertura")
    private String ciudadCobertura;

    @Min(1) @Max(365)
    @Schema(description = "Ciclo de facturación en días", defaultValue = "30")
    private Integer cicloFacturacionDias = 30;
}
