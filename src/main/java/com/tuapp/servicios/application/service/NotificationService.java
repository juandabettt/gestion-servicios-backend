package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.response.NotificationResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.NotificationMapper;
import com.tuapp.servicios.application.port.NotificationPort;
import com.tuapp.servicios.application.port.dto.EmailNotificationRequest;
import com.tuapp.servicios.domain.enums.CanalNotificacion;
import com.tuapp.servicios.domain.enums.EstadoNotificacion;
import com.tuapp.servicios.domain.enums.TipoNotificacion;
import com.tuapp.servicios.domain.model.*;
import com.tuapp.servicios.domain.repository.NotificationLogRepository;
import com.tuapp.servicios.domain.repository.NotificationRepository;
import com.tuapp.servicios.domain.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationLogRepository notificationLogRepository;
    private final NotificationRepository notificationRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final NotificationPort notificationPort;
    private final NotificationMapper notificationMapper;

    @Transactional
    public void notificarFacturaAgregada(Invoice invoice) {
        User user = invoice.getProperty().getUser();
        String monto = invoice.getMontoTotal() != null ? invoice.getMontoTotal().toPlainString() : "?";
        crearNotificacionInApp(user.getId(), invoice.getId(),
                TipoNotificacion.FACTURA_AGREGADA.name(),
                "Nueva factura registrada",
                "Se registró una factura de $" + monto + " para " + invoice.getProperty().getNombre());
    }

    @Transactional
    public void notificarPagoConfirmado(Invoice invoice, PaymentTransaction transaction) {
        User user = invoice.getProperty().getUser();
        String monto = invoice.getMontoTotal() != null ? invoice.getMontoTotal().toPlainString() : "?";
        crearNotificacionInApp(user.getId(), invoice.getId(),
                TipoNotificacion.PAGO_EXITOSO.name(),
                "Pago realizado",
                "Tu pago de $" + monto + " fue procesado exitosamente.");
        String proveedorNombre = invoice.getProveedor() != null
            ? invoice.getProveedor().getNombre()
            : invoice.getProperty().getNombre();
        NotificationLog n = createAndSave(user, TipoNotificacion.PAGO_CONFIRMADO,
                "Pago confirmado — " + proveedorNombre,
                "Tu pago fue procesado exitosamente", invoice.getId());
        sendEmailIfEnabled(user, n);
    }

    @Transactional
    public void notificarPagoFallido(Invoice invoice) {
        User user = invoice.getProperty().getUser();
        String monto = invoice.getMontoTotal() != null ? invoice.getMontoTotal().toPlainString() : "?";
        crearNotificacionInApp(user.getId(), invoice.getId(),
                TipoNotificacion.PAGO_FALLIDO.name(),
                "Pago fallido",
                "No se pudo procesar tu pago de $" + monto + ". Intenta de nuevo.");
    }

    @Transactional
    public void notificarFacturaPorVencer(Invoice invoice, int diasRestantes) {
        User user = invoice.getProperty().getUser();
        String proveedorNombre = invoice.getProveedor() != null
            ? invoice.getProveedor().getNombre()
            : invoice.getProperty().getNombre();
        NotificationLog n = createAndSave(user, TipoNotificacion.FACTURA_POR_VENCER,
                "Tu factura vence en " + diasRestantes + " días",
                "La factura de " + proveedorNombre + " vence pronto", invoice.getId());
        sendEmailIfEnabled(user, n);
    }

    @Transactional
    public void notificarAnomaliaDetectada(User user, AiAnalysis analysis) {
        NotificationLog n = createAndSave(user, TipoNotificacion.ANOMALIA_DETECTADA,
                "Anomalía detectada en tu consumo",
                "Hemos detectado un consumo atípico. Revisa el análisis.", analysis.getId());
        sendEmailIfEnabled(user, n);
    }

    @Transactional
    public void notificarAnalisisListo(User user, AiAnalysis analysis) {
        NotificationLog n = createAndSave(user, TipoNotificacion.ANALISIS_LISTO,
                "Tu análisis de consumo está listo",
                "Hemos generado nuevas recomendaciones para tu hogar.", analysis.getId());
        sendEmailIfEnabled(user, n);
    }

    @Transactional
    public void notificarAutoPagoEjecutado(User user, Invoice invoice) {
        String monto = invoice.getMontoTotal() != null ? invoice.getMontoTotal().toPlainString() : "?";
        crearNotificacionInApp(user.getId(), invoice.getId(),
                TipoNotificacion.AUTOPAGO_EJECUTADO.name(),
                "Autopago ejecutado",
                "Se pagó automáticamente tu factura de $" + monto + ".");
        String proveedorNombre = invoice.getProveedor() != null
            ? invoice.getProveedor().getNombre()
            : invoice.getProperty().getNombre();
        createAndSave(user, TipoNotificacion.AUTOPAGO_EJECUTADO,
                "Autopago ejecutado",
                "Se ejecutó un pago automático para " + proveedorNombre, invoice.getId());
    }

    @Transactional
    public void notificarAutoPagoFallido(User user, Invoice invoice, String motivo) {
        createAndSave(user, TipoNotificacion.AUTOPAGO_FALLIDO,
                "Autopago no ejecutado — acción requerida",
                "No se pudo pagar automáticamente: " + motivo, invoice.getId());
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getByUsuario(UUID userId, boolean soloNoLeidas, Pageable pageable) {
        Page<Notification> page = soloNoLeidas
            ? notificationRepository.findByUsuarioIdAndLeidaFalseOrderByCreatedAtDesc(userId, pageable)
            : notificationRepository.findByUsuarioIdOrderByCreatedAtDesc(userId, pageable);
        return page.map(notificationMapper::toResponse);
    }

    @Transactional
    public void marcarLeida(UUID notificationId) {
        Notification notif = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new ResourceNotFoundException("Notificación", notificationId));
        notif.setLeida(true);
        notificationRepository.save(notif);
    }

    @Transactional
    public void marcarTodasLeidas(UUID userId) {
        notificationRepository.findByUsuarioIdAndLeidaFalse(userId)
            .forEach(n -> {
                n.setLeida(true);
                notificationRepository.save(n);
            });
    }

    private void crearNotificacionInApp(UUID usuarioId, UUID facturaId, String tipo, String titulo, String mensaje) {
        if (notificationRepository.existsByFacturaIdAndTipo(facturaId, tipo)) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .usuarioId(usuarioId)
                .facturaId(facturaId)
                .tipo(tipo)
                .titulo(titulo)
                .mensaje(mensaje)
                .build());
    }

    private NotificationLog createAndSave(User user, TipoNotificacion tipo, String asunto,
                                           String cuerpo, UUID referenciaId) {
        NotificationLog n = NotificationLog.builder()
                .user(user).tipo(tipo).canal(CanalNotificacion.EMAIL)
                .estado(EstadoNotificacion.PENDIENTE)
                .asunto(asunto).cuerpoResumen(cuerpo).referenciaId(referenciaId)
                .build();
        return notificationLogRepository.save(n);
    }

    private void sendEmailIfEnabled(User user, NotificationLog notification) {
        UserPreferences prefs = preferencesRepository.findByUserId(user.getId()).orElse(null);
        if (prefs != null && Boolean.TRUE.equals(prefs.getNotificacionesEmail())) {
            try {
                notificationPort.sendEmail(EmailNotificationRequest.builder()
                        .destinatario(user.getEmail()).asunto(notification.getAsunto())
                        .plantilla("generic")
                        .variables(Map.of("cuerpo", notification.getCuerpoResumen()))
                        .build());
                notification.setEstado(EstadoNotificacion.ENVIADA);
            } catch (Exception e) {
                log.warn("Error enviando email de notificación");
                notification.setEstado(EstadoNotificacion.FALLIDA);
            }
            notificationLogRepository.save(notification);
        }
    }
}
