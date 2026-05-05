package com.tuapp.servicios.infrastructure.scheduler;

import com.tuapp.servicios.application.service.JobQueueService;
import com.tuapp.servicios.application.service.NotificationService;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.enums.MetodoPago;
import com.tuapp.servicios.domain.enums.TipoJob;
import com.tuapp.servicios.domain.enums.TipoServicio;
import com.tuapp.servicios.domain.model.AutoPayRule;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.model.UserPreferences;
import com.tuapp.servicios.domain.repository.AutoPayRuleRepository;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import com.tuapp.servicios.domain.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoPayScheduler {

    private final AutoPayRuleRepository autoPayRuleRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final JobQueueService jobQueueService;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 9 * * *")
    @Transactional
    public void evaluateAutoPayRules() {
        log.info("Evaluando reglas de autopago...");

        List<AutoPayRule> reglasActivas = autoPayRuleRepository.findByActivaTrueAndDeletedAtIsNull();
        int autopagosEncolados = 0;

        for (AutoPayRule regla : reglasActivas) {
            LocalDate fechaLimite = LocalDate.now().plusDays(regla.getDiasAntesVencimiento());
            UUID userId = regla.getUsuario().getId();

            List<Invoice> facturasPendientes = resolverFacturas(regla, userId, fechaLimite);

            MetodoPago metodoPago = resolverMetodoPago(userId);

            for (Invoice factura : facturasPendientes) {
                if (regla.getMontoMaximo() != null &&
                        factura.getMontoTotal() != null &&
                        factura.getMontoTotal().compareTo(regla.getMontoMaximo()) > 0) {
                    notificationService.notificarAutoPagoFallido(
                            regla.getUsuario(), factura,
                            "Monto excede el límite configurado (" + regla.getMontoMaximo() + ")");
                    log.warn("Autopago omitido — monto supera límite configurado");
                } else {
                    jobQueueService.enqueue(TipoJob.AUTOPAGO, Map.of(
                            "invoiceId", factura.getId().toString(),
                            "userId", userId.toString(),
                            "metodoPago", metodoPago.name(),
                            "ruleId", regla.getId().toString()
                    ));
                    autopagosEncolados++;
                }
            }

            regla.setUltimaEjecucion(LocalDateTime.now());
            autoPayRuleRepository.save(regla);
        }

        log.info("Evaluación de autopagos completada — jobs encolados: {}", autopagosEncolados);
    }

    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void alertarFacturasPorVencer() {
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

    private List<Invoice> resolverFacturas(AutoPayRule regla, UUID userId, LocalDate fechaLimite) {
        if ("TODOS".equalsIgnoreCase(regla.getTipoServicio())) {
            return invoiceRepository.findPendientesByUserIdAndVencimiento(userId, fechaLimite);
        }
        TipoServicio ts = TipoServicio.valueOf(regla.getTipoServicio());
        return invoiceRepository.findPendientesByUserIdAndTipoServicioAndVencimiento(userId, ts, fechaLimite);
    }

    private MetodoPago resolverMetodoPago(UUID userId) {
        return userPreferencesRepository.findByUserId(userId)
                .map(UserPreferences::getMetodoPagoDefault)
                .filter(m -> m != null)
                .orElse(MetodoPago.PSE);
    }
}
