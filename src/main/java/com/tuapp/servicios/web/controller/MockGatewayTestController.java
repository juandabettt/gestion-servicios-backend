package com.tuapp.servicios.web.controller;

import com.tuapp.servicios.infrastructure.adapter.payment.MockPaymentGatewayAdapter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/test/mock-gateway")
@RequiredArgsConstructor
@Profile("local")
@Tag(name = "Test - Mock Gateway", description = "Endpoints de prueba del mock de pasarela (solo perfil local)")
public class MockGatewayTestController {

    private final MockPaymentGatewayAdapter mockGatewayAdapter;

    @PostMapping("/confirm-pse/{transactionId}")
    @Operation(summary = "Confirmar manualmente una transacción PSE pendiente (solo testing)")
    public ResponseEntity<String> confirmPse(@PathVariable String transactionId) {
        mockGatewayAdapter.confirmPseTransaction(transactionId);
        return ResponseEntity.ok("Transacción PSE confirmada: " + transactionId);
    }
}
