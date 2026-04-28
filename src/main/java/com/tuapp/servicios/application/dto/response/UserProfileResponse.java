package com.tuapp.servicios.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@Schema(description = "Datos del perfil del usuario autenticado")
public class UserProfileResponse {
    private UUID id;
    private String nombre;
    private String email;
    private String telefono;
    private String ciudad;
    private String documento;
}
