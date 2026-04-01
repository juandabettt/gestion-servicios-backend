package com.tuapp.servicios.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data @Builder
@Schema(description = "Respuesta de autenticación")
public class AuthResponse {
    @Schema(description = "Access token JWT (expira en 15 min)")
    private String accessToken;
    @Schema(description = "Refresh token (expira en 7 días)")
    private String refreshToken;
    @Schema(description = "Tipo de token", example = "Bearer")
    private String tokenType;
    @Schema(description = "ID del usuario")
    private UUID userId;
    @Schema(description = "Nombre del usuario")
    private String nombre;
    @Schema(description = "Rol del usuario")
    private String rol;
}
