package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Datos para registro de nuevo usuario")
public class RegisterRequest {
    @NotBlank @Size(min = 2, max = 150)
    @Schema(description = "Nombre completo", example = "Juan Pérez")
    private String nombre;

    @NotBlank @Email @Size(max = 255)
    @Schema(description = "Email", example = "juan@example.com")
    private String email;

    @NotBlank @Size(min = 8, max = 100)
    @Schema(description = "Contraseña (mínimo 8 caracteres)")
    private String password;

    @Size(max = 20)
    @Schema(description = "Teléfono", example = "+573001234567")
    private String telefono;
}
