package com.tuapp.servicios.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "Token de refresco")
public class RefreshTokenRequest {
    @NotBlank
    @Schema(description = "Refresh token válido")
    private String refreshToken;
}
