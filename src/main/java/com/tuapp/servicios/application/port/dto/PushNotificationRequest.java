package com.tuapp.servicios.application.port.dto;

import lombok.Builder;
import lombok.Data;
import java.util.Map;

@Data @Builder
public class PushNotificationRequest {
    private String userId;
    private String titulo;
    private String cuerpo;
    private Map<String, String> datos;
}
