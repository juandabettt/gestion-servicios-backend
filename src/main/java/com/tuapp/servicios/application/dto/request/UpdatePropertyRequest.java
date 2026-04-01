package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Datos para actualizar una propiedad")
public class UpdatePropertyRequest {
    @Size(max = 100)
    @Schema(description = "Nombre de la propiedad")
    private String nombre;

    @Size(max = 255)
    @Schema(description = "Dirección")
    private String direccion;

    @Size(max = 100)
    @Schema(description = "Ciudad")
    private String ciudad;

    @Schema(description = "¿Es la propiedad principal?")
    private Boolean esPrincipal;
}
