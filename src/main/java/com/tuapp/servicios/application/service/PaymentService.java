package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.ProcessPaymentRequest;
import com.tuapp.servicios.application.dto.response.PaymentInitiateResponse;
import com.tuapp.servicios.application.dto.response.PaymentTransactionResponse;
import com.tuapp.servicios.application.exception.BusinessException;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.PaymentTransactionMapper;
import com.tuapp.servicios.application.port.PaymentGatewayPort;
import com.tuapp.servicios.application.port.dto.PaymentInitiationResult;
import com.tuapp.servicios.application.port.dto.PaymentRequest;
import com.tuapp.servicios.domain.enums.*;
import com.tuapp.servicios.domain.model.*;
import com.tuapp.servicios.domain.repository.*;
import com.tuapp.servicios.infrastructure.security.InvoiceOwnershipValidator;
import com.tuapp.servicios.infrastructure.security.WebhookSignatureValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentTransactionRepository transactionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PaymentGatewayPort paymentGatewayPort;
    private final InvoiceOwnershipValidator ownershipValidator;
    private final PaymentTransactionMapper transactionMapper;
    private final WebhookSignatureValidator webhookSignatureValidator;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Transactional
    public PaymentInitiateResponse processPayment(ProcessPaymentRequest request, UUID userId) {
        Object existing = redisTemplate.opsForValue().get("idempotency:" + request.getIdempotencyKey());
        if (existing != null) {
            log.info("Pago duplicado detectado, devolviendo resultado anterior");
            return PaymentInitiateResponse.builder()
                    .estado("DUPLICADO")
                    .message("Este pago ya fue procesado anteriormente")
                    .build();
        }
        Invoice invoice = ownershipValidator.validateAndGet(request.getInvoiceId(), userId);
        if (invoice.getEstado() != EstadoFactura.PENDIENTE &&
            invoice.getEstado() != EstadoFactura.VENCIDA) {
            throw new BusinessException(
                "La factura debe estar en estado PENDIENTE o VENCIDA para poder pagarla. " +
                "Estado actual: " + invoice.getEstado()
            );
        }
        invoice.setEstado(EstadoFactura.PROCESANDO_PAGO);
        invoiceRepository.save(invoice);

        PaymentTransaction transaction = PaymentTransaction.builder()
                .factura(invoice)
                .montoTransaccion(invoice.getMontoTotal())
                .metodoPago(request.getMetodoPago())
                .bancoOrigen(request.getBancoOrigen())
                .idempotencyKey(request.getIdempotencyKey())
                .build();
        transaction = transactionRepository.save(transaction);

        PaymentInitiationResult result = paymentGatewayPort.initiatePayment(
                PaymentRequest.builder()
                        .invoiceId(invoice.getId())
                        .amount(invoice.getMontoTotal())
                        .metodoPago(request.getMetodoPago())
                        .bancoOrigen(request.getBancoOrigen())
                        .idempotencyKey(request.getIdempotencyKey())
                        .codigoConvenioRecaudo(invoice.getProveedor().getCodigoConvenioRecaudo())
                        .numeroReferencia(invoice.getNumeroReferencia())
                        .build());

        transaction.setIdTransaccionPasarela(result.getGatewayTransactionId());
        transaction.setEstadoTransaccion(result.getEstado());
        transaction.setUrlRedireccionPse(result.getUrlRedireccionPse());

        if (result.getEstado() == EstadoTransaccion.APROBADA) {
            transaction.setFechaConfirmacion(LocalDateTime.now());
            invoice.setEstado(EstadoFactura.PAGADA);
            notificationService.notificarPagoConfirmado(invoice, transaction);
        } else if (result.getEstado() == EstadoTransaccion.RECHAZADA) {
            invoice.setEstado(EstadoFactura.PENDIENTE);
            notificationService.notificarPagoFallido(invoice);
        }

        transactionRepository.save(transaction);
        invoiceRepository.save(invoice);
        redisTemplate.opsForValue().set("idempotency:" + request.getIdempotencyKey(),
                transaction.getId().toString(), 24, TimeUnit.HOURS);
        auditService.log(userId, "PAYMENT_INITIATED", "PaymentTransaction", transaction.getId(),
                result.isExitoso() ? ResultadoAudit.EXITO : ResultadoAudit.FALLO, result.getMensaje());

        return PaymentInitiateResponse.builder()
                .transactionId(transaction.getId())
                .estado(transaction.getEstadoTransaccion().name())
                .urlRedireccion(transaction.getUrlRedireccionPse())
                .message(result.getMensaje())
                .build();
    }

    @Transactional(readOnly = true)
    public PaymentTransactionResponse getTransactionStatus(UUID transactionId, UUID userId) {
        PaymentTransaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transacción", transactionId));
        ownershipValidator.validateAndGet(transaction.getFactura().getId(), userId);
        return transactionMapper.toResponse(transaction);
    }

    @Transactional
    public void processWebhook(String payload, String signature) {
        if (!webhookSignatureValidator.isValid(payload, signature)) {
            auditService.log(null, "WEBHOOK_INVALID_SIGNATURE", "PaymentTransaction", null,
                    ResultadoAudit.FALLO, "Firma HMAC inválida en webhook");
            throw new BusinessException("Firma del webhook inválida", HttpStatus.UNAUTHORIZED);
        }
        log.info("Webhook de pasarela recibido y validado — procesamiento async pendiente");
    }

    @Transactional(readOnly = true)
    public Page<PaymentTransactionResponse> listByUser(UUID userId, Pageable pageable) {
        return transactionRepository.findByUserId(userId, pageable).map(transactionMapper::toResponse);
    }
}
