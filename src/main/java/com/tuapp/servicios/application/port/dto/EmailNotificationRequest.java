package com.tuapp.servicios.application.port.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data @Builder
public class EmailNotificationRequest {
    private String destinatario;
    private String asunto;
    private String plantilla;
    private Map<String, Object> variables;
}
