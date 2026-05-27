package com.tuapp.servicios.application.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class StripeService {

    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    public Map<String, Object> createPaymentIntent(BigDecimal amount, String invoiceId) throws StripeException {
        Stripe.apiKey = stripeSecretKey;

        long amountInCents = amount.multiply(BigDecimal.valueOf(100)).longValue();

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency("cop")
                .setDescription("Pago de factura: " + invoiceId)
                .putMetadata("invoiceId", invoiceId)
                .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        Map<String, Object> response = new HashMap<>();
        response.put("clientSecret", paymentIntent.getClientSecret());
        response.put("paymentIntentId", paymentIntent.getId());
        response.put("status", paymentIntent.getStatus());

        log.info("Payment Intent creado: {} para factura {}", paymentIntent.getId(), invoiceId);
        return response;
    }

    public PaymentIntent retrievePaymentIntent(String paymentIntentId) throws StripeException {
        Stripe.apiKey = stripeSecretKey;
        return PaymentIntent.retrieve(paymentIntentId);
    }
}
