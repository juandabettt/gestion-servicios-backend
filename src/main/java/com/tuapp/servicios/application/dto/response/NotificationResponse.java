package com.tuapp.servicios.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
@Schema(description = "Notificación del usuario")
public class NotificationResponse {
    private UUID id;
    private UUID facturaId;
    private String tipo;
    private String titulo;
    private String mensaje;
    private boolean leida;
    private LocalDateTime createdAt;
}
