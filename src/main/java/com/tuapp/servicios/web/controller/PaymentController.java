package com.tuapp.servicios.web.controller;

import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.tuapp.servicios.application.dto.request.ConfirmPaymentRequest;
import com.tuapp.servicios.application.dto.request.CreatePaymentIntentRequest;
import com.tuapp.servicios.application.dto.request.ProcessPaymentRequest;
import com.tuapp.servicios.application.dto.response.InvoiceResponse;
import com.tuapp.servicios.application.dto.response.PaymentInitiateResponse;
import com.tuapp.servicios.application.dto.response.PaymentTransactionResponse;
import com.tuapp.servicios.application.exception.BusinessException;
import com.tuapp.servicios.application.mapper.InvoiceMapper;
import com.tuapp.servicios.application.service.PaymentService;
import com.tuapp.servicios.application.service.StripeService;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import com.tuapp.servicios.domain.repository.UserRepository;
import com.tuapp.servicios.infrastructure.security.InvoiceOwnershipValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Pagos", description = "Procesamiento de pagos de facturas")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;
    private final StripeService stripeService;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceMapper invoiceMapper;
    private final InvoiceOwnershipValidator ownershipValidator;

    @PostMapping("/process")
    @Operation(summary = "Iniciar pago de una factura")
    public ResponseEntity<PaymentInitiateResponse> process(
            @Valid @RequestBody ProcessPaymentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(paymentService.processPayment(request, userId));
    }

    @GetMapping("/{transactionId}/status")
    @Operation(summary = "Consultar estado de una transacción")
    public ResponseEntity<PaymentTransactionResponse> getStatus(
            @PathVariable UUID transactionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(paymentService.getTransactionStatus(transactionId, userId));
    }

    @PostMapping("/webhook/gateway")
    @SecurityRequirements
    @Operation(summary = "Webhook de pasarela de pagos (autenticación HMAC, no JWT)")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader("X-Gateway-Signature") String signature) {
        paymentService.processWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @Operation(summary = "Listar transacciones del usuario")
    public ResponseEntity<Page<PaymentTransactionResponse>> list(
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(paymentService.listByUser(userId, pageable));
    }

    @PostMapping("/create-payment-intent")
    @Operation(summary = "Crear Payment Intent de Stripe")
    public ResponseEntity<Map<String, Object>> createPaymentIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            UUID userId = resolveUserId(userDetails);
            Invoice invoice = ownershipValidator.validateAndGet(request.getInvoiceId(), userId);

            if (invoice.getEstado() != EstadoFactura.PENDIENTE &&
                invoice.getEstado() != EstadoFactura.VENCIDA) {
                throw new BusinessException("La factura no puede ser pagada en estado: " + invoice.getEstado(), HttpStatus.BAD_REQUEST);
            }

            Map<String, Object> paymentIntent = stripeService.createPaymentIntent(
                invoice.getMontoTotal(),
                invoice.getId().toString()
            );

            return ResponseEntity.ok(paymentIntent);
        } catch (StripeException e) {
            log.error("Error creando payment intent: {}", e.getMessage());
            throw new BusinessException("Error al procesar el pago", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/confirm-payment")
    @Operation(summary = "Confirmar pago de Stripe")
    public ResponseEntity<InvoiceResponse> confirmPayment(
            @Valid @RequestBody ConfirmPaymentRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            UUID userId = resolveUserId(userDetails);
            Invoice invoice = ownershipValidator.validateAndGet(request.getInvoiceId(), userId);

            PaymentIntent paymentIntent = stripeService.retrievePaymentIntent(request.getPaymentIntentId());

            if (!"succeeded".equals(paymentIntent.getStatus())) {
                throw new BusinessException("El pago no fue exitoso", HttpStatus.BAD_REQUEST);
            }

            invoice.setEstado(EstadoFactura.PAGADA);
            invoice.setFechaPago(LocalDateTime.now());
            invoice.setMetodoPago("stripe");
            invoiceRepository.save(invoice);

            log.info("Factura {} pagada exitosamente via Stripe", invoice.getId());

            return ResponseEntity.ok(invoiceMapper.toResponse(invoice));
        } catch (StripeException e) {
            log.error("Error confirmando pago: {}", e.getMessage());
            throw new BusinessException("Error al confirmar el pago", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
    }
}
