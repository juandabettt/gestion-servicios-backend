package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Datos para crear una propiedad/inmueble")
public class CreatePropertyRequest {
    @NotBlank @Size(max = 100)
    @Schema(description = "Nombre de la propiedad", example = "Casa principal")
    private String nombre;

    @Size(max = 255)
    @Schema(description = "Dirección", example = "Calle 10 # 5-20")
    private String direccion;

    @Size(max = 100)
    @Schema(description = "Ciudad", example = "Pasto")
    private String ciudad;

    @Schema(description = "¿Es la propiedad principal?", defaultValue = "false")
    private Boolean esPrincipal = false;
}
