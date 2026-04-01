package com.tuapp.servicios.infrastructure.scheduler;

import com.tuapp.servicios.application.service.JobQueueService;
import com.tuapp.servicios.application.service.NotificationService;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.enums.TipoJob;
import com.tuapp.servicios.domain.model.AutoPayRule;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.repository.AutoPayRuleRepository;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoPayScheduler {

    private final AutoPayRuleRepository autoPayRuleRepository;
    private final InvoiceRepository invoiceRepository;
    private final JobQueueService jobQueueService;
    private final NotificationService notificationService;

    // Ejecutar cada día a las 9am
    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void evaluateAutoPayRules() {
        log.info("Evaluando reglas de autopago...");

        List<AutoPayRule> reglasActivas = autoPayRuleRepository.findByActivoTrueAndDeletedAtIsNull();
        int autopagosEncolados = 0;

        for (AutoPayRule regla : reglasActivas) {
            LocalDate fechaLimite = LocalDate.now().plusDays(regla.getDiasAntesVencimiento());

            List<Invoice> facturasPendientes = invoiceRepository
                    .findPendientesByPropertyAndVencimiento(
                            regla.getProperty().getId(), fechaLimite);

            for (Invoice factura : facturasPendientes) {
                if (regla.getMontoMaximo() != null &&
                        factura.getMontoTotal().compareTo(regla.getMontoMaximo()) > 0) {
                    // Monto supera el límite — NO pagar, notificar
                    notificationService.notificarAutoPagoFallido(
                            regla.getProperty().getUser(), factura,
                            "Monto excede el límite configurado (" + regla.getMontoMaximo() + ")");
                    log.warn("Autopago omitido — monto supera límite configurado");
                } else {
                    // Encolar autopago
                    jobQueueService.enqueue(TipoJob.AUTOPAGO, Map.of(
                            "invoiceId", factura.getId().toString(),
                            "userId", regla.getProperty().getUser().getId().toString(),
                            "metodoPago", regla.getMetodoPago().name()
                    ));
                    autopagosEncolados++;
                }
            }

            regla.setUltimaEjecucion(LocalDateTime.now());
            autoPayRuleRepository.save(regla);
        }

        log.info("Evaluación de autopagos completada — jobs encolados: {}", autopagosEncolados);
    }

    // Alertar facturas próximas a vencer (notificaciones proactivas)
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void alertarFacturasPorVencer() {
        // Alertar con 5 días de anticipación por defecto
        LocalDate fechaLimite = LocalDate.now().plusDays(5);
        List<Invoice> facturasPorVencer = invoiceRepository.findAllPendientesProximasVencer(fechaLimite);

        for (Invoice factura : facturasPorVencer) {
            long diasRestantes = java.time.temporal.ChronoUnit.DAYS
                    .between(LocalDate.now(), factura.getFechaVencimiento());
            notificationService.notificarFacturaPorVencer(factura, (int) diasRestantes);
        }

        if (!facturasPorVencer.isEmpty()) {
            log.info("Alertas de vencimiento enviadas: {} facturas", facturasPorVencer.size());
        }
    }

    // Marcar facturas vencidas
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void marcarFacturasVencidas() {
        List<Invoice> vencidas = invoiceRepository.findVencidas(LocalDate.now());
        for (Invoice factura : vencidas) {
            factura.setEstado(EstadoFactura.VENCIDA);
            invoiceRepository.save(factura);
        }
        if (!vencidas.isEmpty()) {
            log.info("Facturas marcadas como vencidas: {}", vencidas.size());
        }
    }
}
