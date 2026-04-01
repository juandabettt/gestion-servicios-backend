package com.tuapp.servicios.infrastructure.scheduler.handler;

import com.tuapp.servicios.application.port.NotificationPort;
import com.tuapp.servicios.application.port.dto.EmailNotificationRequest;
import com.tuapp.servicios.domain.enums.EstadoNotificacion;
import com.tuapp.servicios.domain.model.NotificationLog;
import com.tuapp.servicios.domain.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationJobHandler {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationPort notificationPort;

    @Transactional
    public void handle(String payloadJson) {
        List<NotificationLog> pendientes = notificationLogRepository
                .findPendientesParaEnviar(PageRequest.of(0, 50));

        for (NotificationLog notification : pendientes) {
            notification.setIntentos(notification.getIntentos() + 1);
            try {
                notificationPort.sendEmail(EmailNotificationRequest.builder()
                        .destinatario(notification.getUser().getEmail())
                        .asunto(notification.getAsunto())
                        .plantilla("generic")
                        .variables(Map.of("cuerpo", notification.getCuerpoResumen() != null
                                ? notification.getCuerpoResumen() : ""))
                        .build());
                notification.setEstado(EstadoNotificacion.ENVIADA);
            } catch (Exception e) {
                log.warn("Error enviando notificación id={} — intento #{}", notification.getId(), notification.getIntentos());
                if (notification.getIntentos() >= 3) {
                    notification.setEstado(EstadoNotificacion.FALLIDA);
                }
            }
            notificationLogRepository.save(notification);
        }

        log.info("Notificaciones procesadas: {}", pendientes.size());
    }
}
