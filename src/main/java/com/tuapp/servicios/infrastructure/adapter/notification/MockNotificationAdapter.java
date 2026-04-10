package com.tuapp.servicios.infrastructure.adapter.notification;

import com.tuapp.servicios.application.port.NotificationPort;
import com.tuapp.servicios.application.port.dto.EmailNotificationRequest;
import com.tuapp.servicios.application.port.dto.PushNotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "production"})
@Slf4j
public class MockNotificationAdapter implements NotificationPort {

    @Override
    public void sendEmail(EmailNotificationRequest request) {
        log.info("Mock Email → Para: {} | Asunto: {}", request.getDestinatario(), request.getAsunto());
    }

    @Override
    public void sendPushNotification(PushNotificationRequest request) {
        log.info("Mock Push → Usuario: {} | Título: {} | Cuerpo: {}",
                request.getUserId(), request.getTitulo(), request.getCuerpo());
    }
}
