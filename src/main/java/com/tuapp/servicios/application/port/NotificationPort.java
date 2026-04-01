package com.tuapp.servicios.application.port;

import com.tuapp.servicios.application.port.dto.EmailNotificationRequest;
import com.tuapp.servicios.application.port.dto.PushNotificationRequest;

public interface NotificationPort {
    void sendEmail(EmailNotificationRequest request);
    void sendPushNotification(PushNotificationRequest request);
}
