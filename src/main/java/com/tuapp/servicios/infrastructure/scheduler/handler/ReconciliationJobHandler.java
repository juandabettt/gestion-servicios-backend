package com.tuapp.servicios.infrastructure.scheduler.handler;

import com.tuapp.servicios.application.port.PaymentGatewayPort;
import com.tuapp.servicios.application.port.dto.TransactionStatusResult;
import com.tuapp.servicios.application.service.AuditService;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.enums.EstadoTransaccion;
import com.tuapp.servicios.domain.enums.ResultadoAudit;
import com.tuapp.servicios.domain.model.PaymentTransaction;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import com.tuapp.servicios.domain.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJobHandler {

    private final PaymentTransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final AuditService auditService;

    // Ejecutar cada noche a las 2am
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void reconcilePayments() {
        log.info("Iniciando conciliación diaria de pagos");

        // 1. PSE pendientes entre 24h y 72h atrás
        List<PaymentTransaction> psePendientes = transactionRepository.findByEstadoAndCreatedAtBetween(
                EstadoTransaccion.PENDIENTE_PSE,
                LocalDateTime.now().minusHours(72),
                LocalDateTime.now().minusHours(24));

        int conciliados = 0;
        for (PaymentTransaction transaction : psePendientes) {
            try {
                TransactionStatusResult status = paymentGatewayPort
                        .getTransactionStatus(transaction.getIdTransaccionPasarela());

                if (status.getEstado() != transaction.getEstadoTransaccion()) {
                    transaction.setEstadoTransaccion(status.getEstado());
                    if (status.getEstado() == EstadoTransaccion.APROBADA) {
                        transaction.setFechaConfirmacion(LocalDateTime.now());
                        transaction.getFactura().setEstado(EstadoFactura.PAGADA);
                        invoiceRepository.save(transaction.getFactura());
                    }
                    transactionRepository.save(transaction);
                    conciliados++;
                    auditService.log(null, "RECONCILIATION_UPDATED", "PaymentTransaction",
                            transaction.getId(), ResultadoAudit.EXITO,
                            "Estado actualizado a " + status.getEstado());
                }
            } catch (Exception e) {
                log.warn("Error conciliando transacción {}", transaction.getId());
            }
        }

        // 2. PSE pendientes > 72h → EXPIRADA
        List<PaymentTransaction> expiradas = transactionRepository.findByEstadoAndCreatedAtBetween(
                EstadoTransaccion.PENDIENTE_PSE,
                LocalDateTime.now().minusDays(30),
                LocalDateTime.now().minusHours(72));

        for (PaymentTransaction transaction : expiradas) {
            transaction.setEstadoTransaccion(EstadoTransaccion.EXPIRADA);
            transaction.getFactura().setEstado(EstadoFactura.PENDIENTE);
            invoiceRepository.save(transaction.getFactura());
            transactionRepository.save(transaction);
            conciliados++;
        }

        // 3. Inconsistencias: APROBADA pero factura no PAGADA
        List<PaymentTransaction> inconsistentes = transactionRepository.findAprobadaSinFacturaPagada();
        for (PaymentTransaction transaction : inconsistentes) {
            transaction.getFactura().setEstado(EstadoFactura.PAGADA);
            invoiceRepository.save(transaction.getFactura());
            auditService.log(null, "RECONCILIATION_INCONSISTENCY_FIXED", "Invoice",
                    transaction.getFactura().getId(), ResultadoAudit.EXITO,
                    "Inconsistencia detectada y corregida: factura marcada como PAGADA");
        }

        log.info("Conciliación completada — registros actualizados: {}, inconsistencias: {}",
                conciliados, inconsistentes.size());
    }

    @Transactional
    public void handle(String payloadJson) {
        reconcilePayments();
    }
}
