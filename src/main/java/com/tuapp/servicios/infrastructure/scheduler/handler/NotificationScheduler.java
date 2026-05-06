package com.tuapp.servicios.infrastructure.scheduler.handler;

import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.model.Notification;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import com.tuapp.servicios.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationScheduler {

    private final InvoiceRepository invoiceRepository;
    private final NotificationRepository notificationRepository;

    @Scheduled(cron = "0 0 8 * * *")
    public void generarNotificacionesVencimiento() {
        log.info("Ejecutando scheduler de notificaciones de vencimiento");

        LocalDate hoy = LocalDate.now();

        procesarNotificaciones(hoy.plusDays(7), "FACTURA_VENCE_7_DIAS",
            "Factura próxima a vencer",
            "Tu factura vence en 7 días. Monto: $");

        procesarNotificaciones(hoy.plusDays(3), "FACTURA_VENCE_3_DIAS",
            "¡Factura vence pronto!",
            "Tu factura vence en 3 días. Evita recargos.");

        procesarNotificaciones(hoy, "FACTURA_VENCE_HOY",
            "Factura vence hoy",
            "Tu factura vence hoy. Paga ahora.");

        procesarNotificacionesVencidas(hoy);
    }

    private void procesarNotificaciones(LocalDate fecha, String tipo, String titulo, String mensajeBase) {
        List<Invoice> facturas = invoiceRepository.findByFechaVencimientoAndEstado(fecha, EstadoFactura.PENDIENTE);

        facturas.forEach(factura -> {
            if (!notificationRepository.existsByFacturaIdAndTipo(factura.getId(), tipo)) {
                String mensaje;
                if (mensajeBase.endsWith("$")) {
                    String monto = factura.getMontoTotal() != null ? factura.getMontoTotal().toPlainString() : "?";
                    mensaje = mensajeBase + monto;
                } else {
                    mensaje = mensajeBase;
                }
                Notification notif = Notification.builder()
                    .usuarioId(factura.getProperty().getUser().getId())
                    .facturaId(factura.getId())
                    .tipo(tipo)
                    .titulo(titulo)
                    .mensaje(mensaje)
                    .build();
                notificationRepository.save(notif);
                log.info("Notificación creada: {} para factura {}", tipo, factura.getId());
            }
        });
    }

    private void procesarNotificacionesVencidas(LocalDate hoy) {
        List<Invoice> vencidas = invoiceRepository.findByFechaVencimientoAndEstadoIn(
            hoy.minusDays(1),
            Arrays.asList(EstadoFactura.PENDIENTE, EstadoFactura.VENCIDA)
        );

        vencidas.forEach(factura -> {
            if (!notificationRepository.existsByFacturaIdAndTipo(factura.getId(), "FACTURA_VENCIDA")) {
                Notification notif = Notification.builder()
                    .usuarioId(factura.getProperty().getUser().getId())
                    .facturaId(factura.getId())
                    .tipo("FACTURA_VENCIDA")
                    .titulo("Factura vencida")
                    .mensaje("Tu factura venció. Realiza el pago lo antes posible.")
                    .build();
                notificationRepository.save(notif);
                log.info("Notificación FACTURA_VENCIDA creada para factura {}", factura.getId());
            }
        });
    }
}
