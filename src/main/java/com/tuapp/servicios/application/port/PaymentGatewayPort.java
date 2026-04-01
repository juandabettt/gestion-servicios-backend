package com.tuapp.servicios.application.port;

import com.tuapp.servicios.application.port.dto.PaymentInitiationResult;
import com.tuapp.servicios.application.port.dto.RefundResult;
import com.tuapp.servicios.application.port.dto.TransactionStatusResult;
import java.math.BigDecimal;

public interface PaymentGatewayPort {
    PaymentInitiationResult initiatePayment(com.tuapp.servicios.application.port.dto.PaymentRequest request);
    TransactionStatusResult getTransactionStatus(String gatewayTransactionId);
    RefundResult refund(String gatewayTransactionId, BigDecimal amount);
    boolean verifyWebhookSignature(String payload, String signature);
}
