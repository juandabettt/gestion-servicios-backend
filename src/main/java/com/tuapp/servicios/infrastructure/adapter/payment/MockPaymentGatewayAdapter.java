package com.tuapp.servicios.infrastructure.adapter.payment;

import com.tuapp.servicios.application.port.PaymentGatewayPort;
import com.tuapp.servicios.application.port.dto.*;
import com.tuapp.servicios.domain.enums.EstadoTransaccion;
import com.tuapp.servicios.domain.enums.MetodoPago;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Profile("local")
@Slf4j
public class MockPaymentGatewayAdapter implements PaymentGatewayPort {

    private final Random random = new Random();
    private final Map<String, EstadoTransaccion> transactionStore = new ConcurrentHashMap<>();

    @Override
    public PaymentInitiationResult initiatePayment(PaymentRequest request) {
        String gatewayId = "MOCK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Mock Gateway: procesando pago para factura {}", request.getInvoiceId());

        if (request.getMetodoPago() == MetodoPago.PSE) {
            transactionStore.put(gatewayId, EstadoTransaccion.PENDIENTE_PSE);
            return PaymentInitiationResult.builder()
                    .gatewayTransactionId(gatewayId)
                    .estado(EstadoTransaccion.PENDIENTE_PSE)
                    .urlRedireccionPse("http://localhost:8080/api/v1/test/mock-gateway/pse-redirect/" + gatewayId)
                    .mensaje("Redirigir al banco para completar el pago PSE")
                    .exitoso(true)
                    .build();
        }

        // 10% de rechazos aleatorios
        if (random.nextInt(10) == 0) {
            transactionStore.put(gatewayId, EstadoTransaccion.RECHAZADA);
            return PaymentInitiationResult.builder()
                    .gatewayTransactionId(gatewayId)
                    .estado(EstadoTransaccion.RECHAZADA)
                    .mensaje("Pago rechazado por el banco (simulado)")
                    .exitoso(false)
                    .build();
        }

        transactionStore.put(gatewayId, EstadoTransaccion.APROBADA);
        return PaymentInitiationResult.builder()
                .gatewayTransactionId(gatewayId)
                .estado(EstadoTransaccion.APROBADA)
                .mensaje("Pago aprobado exitosamente")
                .exitoso(true)
                .build();
    }

    @Override
    public TransactionStatusResult getTransactionStatus(String gatewayTransactionId) {
        EstadoTransaccion estado = transactionStore.getOrDefault(
                gatewayTransactionId, EstadoTransaccion.INICIADA);
        return TransactionStatusResult.builder()
                .gatewayTransactionId(gatewayTransactionId)
                .estado(estado)
                .descripcion("Estado consultado en Mock Gateway")
                .build();
    }

    @Override
    public RefundResult refund(String gatewayTransactionId, BigDecimal amount) {
        return RefundResult.builder()
                .refundId("REFUND-" + UUID.randomUUID().toString().substring(0, 8))
                .montoReembolsado(amount)
                .exitoso(true)
                .mensaje("Reembolso procesado exitosamente (mock)")
                .build();
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        return "mock-valid-signature".equals(signature);
    }

    public void confirmPseTransaction(String gatewayTransactionId) {
        transactionStore.put(gatewayTransactionId, EstadoTransaccion.APROBADA);
        log.info("Mock Gateway: PSE confirmado para transacción {}", gatewayTransactionId);
    }
}
