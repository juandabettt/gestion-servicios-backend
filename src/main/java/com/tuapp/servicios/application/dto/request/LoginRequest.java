package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Schema(description = "Credenciales de inicio de sesión")
public class LoginRequest {
    @NotBlank @Email
    @Schema(description = "Email del usuario")
    private String email;

    @NotBlank
    @Schema(description = "Contraseña")
    private String password;
}
