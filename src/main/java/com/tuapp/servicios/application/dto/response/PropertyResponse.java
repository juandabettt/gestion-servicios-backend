package com.tuapp.servicios.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
@Schema(description = "Datos de una propiedad/inmueble")
public class PropertyResponse {
    private UUID id;
    private String nombre;
    private String direccion;
    private String ciudad;
    private Boolean esPrincipal;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
