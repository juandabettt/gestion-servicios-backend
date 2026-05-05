package com.tuapp.servicios.infrastructure.scheduler.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.port.PaymentGatewayPort;
import com.tuapp.servicios.application.port.dto.PaymentInitiationResult;
import com.tuapp.servicios.application.port.dto.PaymentRequest;
import com.tuapp.servicios.application.service.NotificationService;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.enums.EstadoTransaccion;
import com.tuapp.servicios.domain.enums.MetodoPago;
import com.tuapp.servicios.domain.enums.ResultadoAudit;
import com.tuapp.servicios.application.service.AuditService;
import com.tuapp.servicios.domain.model.AutoPayRule;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.model.PaymentTransaction;
import com.tuapp.servicios.domain.repository.AutoPayRuleRepository;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import com.tuapp.servicios.domain.repository.PaymentTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AutoPayJobHandler {

    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final AutoPayRuleRepository autoPayRuleRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String payloadJson) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
        UUID invoiceId = UUID.fromString((String) payload.get("invoiceId"));
        UUID userId = UUID.fromString((String) payload.get("userId"));
        String metodoPagoStr = (String) payload.get("metodoPago");
        String ruleIdStr = (String) payload.get("ruleId");

        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + invoiceId));

        if (invoice.getEstado() != EstadoFactura.PENDIENTE) {
            log.info("Autopago omitido — factura no está en estado PENDIENTE");
            return;
        }

        invoice.setEstado(EstadoFactura.PROCESANDO_PAGO);
        invoiceRepository.save(invoice);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .factura(invoice)
                .montoTransaccion(invoice.getMontoTotal())
                .metodoPago(MetodoPago.valueOf(metodoPagoStr))
                .idempotencyKey("autopay-" + invoiceId + "-" + System.currentTimeMillis())
                .build();
        transaction = transactionRepository.save(transaction);

        PaymentInitiationResult result = paymentGatewayPort.initiatePayment(
                PaymentRequest.builder()
                        .invoiceId(invoice.getId())
                        .amount(invoice.getMontoTotal())
                        .metodoPago(transaction.getMetodoPago())
                        .idempotencyKey(transaction.getIdempotencyKey())
                        .codigoConvenioRecaudo(invoice.getProveedor().getCodigoConvenioRecaudo())
                        .numeroReferencia(invoice.getNumeroReferencia())
                        .build());

        transaction.setIdTransaccionPasarela(result.getGatewayTransactionId());
        transaction.setEstadoTransaccion(result.getEstado());

        if (result.getEstado() == EstadoTransaccion.APROBADA) {
            transaction.setFechaConfirmacion(LocalDateTime.now());
            invoice.setEstado(EstadoFactura.PAGADA);
            notificationService.notificarAutoPagoEjecutado(invoice.getProperty().getUser(), invoice);
            auditService.log(userId, "AUTOPAGO_EJECUTADO", "Invoice", invoiceId,
                    ResultadoAudit.EXITO, "Autopago ejecutado correctamente");
            incrementarContadorRegla(ruleIdStr);
        } else {
            invoice.setEstado(EstadoFactura.PENDIENTE);
            notificationService.notificarAutoPagoFallido(invoice.getProperty().getUser(), invoice,
                    "La pasarela rechazó el pago automático");
            auditService.log(userId, "AUTOPAGO_FALLIDO", "Invoice", invoiceId,
                    ResultadoAudit.FALLO, result.getMensaje());
        }

        transactionRepository.save(transaction);
        invoiceRepository.save(invoice);
    }

    private void incrementarContadorRegla(String ruleIdStr) {
        if (ruleIdStr == null) return;
        try {
            UUID ruleId = UUID.fromString(ruleIdStr);
            autoPayRuleRepository.findByIdAndDeletedAtIsNull(ruleId).ifPresent(rule -> {
                rule.setTotalPagosRealizados(rule.getTotalPagosRealizados() + 1);
                autoPayRuleRepository.save(rule);
            });
        } catch (IllegalArgumentException e) {
            log.warn("ruleId inválido en payload de autopago: {}", ruleIdStr);
        }
    }
}
