package com.tuapp.servicios.web.controller;

import com.tuapp.servicios.application.dto.request.ProcessPaymentRequest;
import com.tuapp.servicios.application.dto.response.PaymentInitiateResponse;
import com.tuapp.servicios.application.dto.response.PaymentTransactionResponse;
import com.tuapp.servicios.application.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.tuapp.servicios.domain.repository.UserRepository;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos", description = "Procesamiento de pagos de facturas")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

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

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
    }
}
