package com.tuapp.servicios.web.controller;

import com.tuapp.servicios.application.dto.request.CreateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.request.UpdateAutoPayRuleRequest;
import com.tuapp.servicios.application.dto.response.AutoPayRuleResponse;
import com.tuapp.servicios.application.service.AutoPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.tuapp.servicios.domain.repository.UserRepository;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/autopay")
@RequiredArgsConstructor
@Tag(name = "Auto-pagos", description = "Configuración de pagos automáticos programados")
public class AutoPayController {

    private final AutoPayService autoPayService;
    private final UserRepository userRepository;

    @PostMapping("/rules")
    @Operation(summary = "Crear regla de autopago para un proveedor")
    public ResponseEntity<AutoPayRuleResponse> createRule(
            @Valid @RequestBody CreateAutoPayRuleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).body(autoPayService.createRule(request, userId));
    }

    @GetMapping("/rules")
    @Operation(summary = "Listar reglas de autopago activas del usuario")
    public ResponseEntity<Page<AutoPayRuleResponse>> listRules(
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(autoPayService.listByUser(userId, pageable));
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "Modificar una regla de autopago")
    public ResponseEntity<AutoPayRuleResponse> updateRule(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAutoPayRuleRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(autoPayService.updateRule(id, request, userId));
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "Desactivar una regla de autopago (soft delete)")
    public ResponseEntity<Void> deleteRule(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        autoPayService.deleteRule(id, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
    }
}
