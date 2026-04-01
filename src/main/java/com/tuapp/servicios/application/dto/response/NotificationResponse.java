package com.tuapp.servicios.application.dto.response;

import com.tuapp.servicios.domain.enums.CanalNotificacion;
import com.tuapp.servicios.domain.enums.EstadoNotificacion;
import com.tuapp.servicios.domain.enums.TipoNotificacion;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder
@Schema(description = "Notificación del sistema")
public class NotificationResponse {
    private UUID id;
    private TipoNotificacion tipo;
    private CanalNotificacion canal;
    private EstadoNotificacion estado;
    private String asunto;
    private String cuerpoResumen;
    private UUID referenciaId;
    private LocalDateTime createdAt;
}
