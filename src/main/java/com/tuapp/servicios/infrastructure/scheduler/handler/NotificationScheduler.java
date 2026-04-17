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

        procesarNotificaciones(hoy.plusDays(7), "VENCE_EN_7_DIAS",
            "Factura próxima a vencer",
            "Tu factura vence en 7 días. Recuerda pagarla a tiempo.");

        procesarNotificaciones(hoy.plusDays(3), "VENCE_EN_3_DIAS",
            "¡Factura vence pronto!",
            "Tu factura vence en 3 días. Evita recargos pagando hoy.");

        procesarNotificaciones(hoy, "VENCIDA_HOY",
            "Factura vencida hoy",
            "Tu factura venció hoy. Paga ahora para evitar suspensión del servicio.");
    }

    private void procesarNotificaciones(LocalDate fecha, String tipo, String titulo, String mensaje) {
        List<Invoice> facturas = invoiceRepository.findByFechaVencimientoAndEstado(fecha, EstadoFactura.PENDIENTE);

        facturas.forEach(factura -> {
            boolean yaExiste = notificationRepository.existsByFacturaIdAndTipo(factura.getId(), tipo);

            if (!yaExiste) {
                Notification notif = Notification.builder()
                    .usuarioId(factura.getProperty().getUser().getId())
                    .facturaId(factura.getId())
                    .tipo(tipo)
                    .titulo(titulo)
                    .mensaje(mensaje + " Monto: $" + factura.getMontoTotal())
                    .build();
                notificationRepository.save(notif);
                log.info("Notificación creada: {} para factura {}", tipo, factura.getId());
            }
        });
    }
}
