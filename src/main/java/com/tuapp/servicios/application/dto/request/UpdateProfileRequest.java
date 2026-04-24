package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Datos para actualizar el perfil del usuario")
public class UpdateProfileRequest {

    @Size(min = 2, max = 150)
    @Schema(description = "Nombre completo", example = "Juan Pérez")
    private String nombre;

    @Pattern(regexp = "^[0-9]{7,15}$", message = "El teléfono debe contener solo dígitos (7-15)")
    @Schema(description = "Teléfono", example = "3001234567")
    private String telefono;

    @Size(max = 100)
    @Schema(description = "Ciudad de residencia", example = "Bogotá")
    private String ciudad;

    @Size(max = 50)
    @Schema(description = "Número de documento de identidad", example = "1234567890")
    private String documento;
}
