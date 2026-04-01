package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.ProcessPaymentRequest;
import com.tuapp.servicios.application.dto.response.PaymentInitiateResponse;
import com.tuapp.servicios.application.exception.BusinessException;
import com.tuapp.servicios.application.mapper.PaymentTransactionMapper;
import com.tuapp.servicios.application.port.PaymentGatewayPort;
import com.tuapp.servicios.application.port.dto.PaymentInitiationResult;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.enums.EstadoTransaccion;
import com.tuapp.servicios.domain.enums.MetodoPago;
import com.tuapp.servicios.domain.model.*;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import com.tuapp.servicios.domain.repository.PaymentTransactionRepository;
import com.tuapp.servicios.infrastructure.security.InvoiceOwnershipValidator;
import com.tuapp.servicios.infrastructure.security.WebhookSignatureValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentTransactionRepository transactionRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentGatewayPort paymentGatewayPort;
    @Mock private InvoiceOwnershipValidator ownershipValidator;
    @Mock private PaymentTransactionMapper transactionMapper;
    @Mock private WebhookSignatureValidator webhookSignatureValidator;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    void processPayment_withValidRequest_returnsApprovedResponse() {
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setInvoiceId(invoiceId);
        request.setMetodoPago(MetodoPago.NEQUI);
        request.setIdempotencyKey("unique-key-123");

        ProviderCompany proveedor = ProviderCompany.builder()
                .nombre("Test Proveedor").codigoConvenioRecaudo("COD-001").build();

        Invoice invoice = Invoice.builder()
                .estado(EstadoFactura.PENDIENTE)
                .montoTotal(new BigDecimal("150000.00"))
                .proveedor(proveedor)
                .build();
        ReflectionTestUtils.setField(invoice, "id", invoiceId);

        PaymentTransaction savedTransaction = PaymentTransaction.builder()
                .factura(invoice).montoTransaccion(invoice.getMontoTotal())
                .metodoPago(MetodoPago.NEQUI).idempotencyKey("unique-key-123")
                .estadoTransaccion(EstadoTransaccion.APROBADA).build();
        ReflectionTestUtils.setField(savedTransaction, "id", UUID.randomUUID());

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:unique-key-123")).thenReturn(null);
        when(ownershipValidator.validateAndGet(invoiceId, userId)).thenReturn(invoice);
        when(invoiceRepository.save(any())).thenReturn(invoice);
        when(transactionRepository.save(any())).thenReturn(savedTransaction);
        when(paymentGatewayPort.initiatePayment(any())).thenReturn(
                PaymentInitiationResult.builder()
                        .gatewayTransactionId("GW-123")
                        .estado(EstadoTransaccion.APROBADA)
                        .exitoso(true)
                        .mensaje("Pago aprobado")
                        .build());

        PaymentInitiateResponse response = paymentService.processPayment(request, userId);

        assertThat(response.getEstado()).isEqualTo("APROBADA");
        verify(notificationService).notificarPagoConfirmado(any(), any());
    }

    @Test
    void processPayment_withNonPendingInvoice_throwsBusinessException() {
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setInvoiceId(invoiceId);
        request.setMetodoPago(MetodoPago.NEQUI);
        request.setIdempotencyKey("key-456");

        Invoice invoice = Invoice.builder().estado(EstadoFactura.PAGADA).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(any())).thenReturn(null);
        when(ownershipValidator.validateAndGet(invoiceId, userId)).thenReturn(invoice);

        assertThatThrownBy(() -> paymentService.processPayment(request, userId))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("PAGADA");
    }

    @Test
    void processPayment_withDuplicateIdempotencyKey_returnsDuplicateResponse() {
        UUID userId = UUID.randomUUID();

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setIdempotencyKey("already-processed");
        request.setInvoiceId(UUID.randomUUID());
        request.setMetodoPago(MetodoPago.PSE);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("idempotency:already-processed")).thenReturn("some-transaction-id");

        PaymentInitiateResponse response = paymentService.processPayment(request, userId);

        assertThat(response.getEstado()).isEqualTo("DUPLICADO");
        verifyNoInteractions(paymentGatewayPort);
    }

    @Test
    void processWebhook_withInvalidSignature_throwsBusinessException() {
        when(webhookSignatureValidator.isValid(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> paymentService.processWebhook("{\"test\":true}", "bad-sig"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("inválida");
    }

    @Test
    void processWebhook_withValidSignature_completesSuccessfully() {
        when(webhookSignatureValidator.isValid(any(), any())).thenReturn(true);

        assertThatCode(() -> paymentService.processWebhook("{\"event\":\"APPROVED\"}", "valid-sig"))
                .doesNotThrowAnyException();
    }
}
