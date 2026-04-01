package com.tuapp.servicios.application.dto.response;

import com.tuapp.servicios.domain.enums.RolUsuario;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
@Schema(description = "Datos de usuario (vista admin)")
public class UserAdminResponse {
    private UUID id;
    private String nombre;
    private String email;
    private RolUsuario rol;
    private Boolean activo;
    private LocalDateTime ultimoLogin;
    private LocalDateTime createdAt;
}
