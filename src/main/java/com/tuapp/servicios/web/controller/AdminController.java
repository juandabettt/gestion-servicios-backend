package com.tuapp.servicios.web.controller;

import com.tuapp.servicios.application.dto.request.CreateProviderRequest;
import com.tuapp.servicios.application.dto.response.ProviderResponse;
import com.tuapp.servicios.application.dto.response.UserAdminResponse;
import com.tuapp.servicios.application.service.AdminService;
import com.tuapp.servicios.domain.model.AuditLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Operaciones administrativas (requiere rol ADMIN)")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/providers")
    @Operation(summary = "Listar todos los proveedores de servicios")
    public ResponseEntity<Page<ProviderResponse>> listProviders(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.listProviders(pageable));
    }

    @PostMapping("/providers")
    @Operation(summary = "Crear nuevo proveedor de servicios")
    public ResponseEntity<ProviderResponse> createProvider(@Valid @RequestBody CreateProviderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createProvider(request));
    }

    @PutMapping("/providers/{id}")
    @Operation(summary = "Actualizar proveedor existente")
    public ResponseEntity<ProviderResponse> updateProvider(
            @PathVariable UUID id,
            @Valid @RequestBody CreateProviderRequest request) {
        return ResponseEntity.ok(adminService.updateProvider(id, request));
    }

    @GetMapping("/users")
    @Operation(summary = "Listar todos los usuarios (paginado)")
    public ResponseEntity<Page<UserAdminResponse>> listUsers(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.listUsers(pageable));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "Ver log de auditoría (paginado)")
    public ResponseEntity<Page<AuditLog>> getAuditLogs(@PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAuditLogs(pageable));
    }

    @PostMapping("/reconciliation/run")
    @Operation(summary = "Disparar conciliación manual de pagos")
    public ResponseEntity<Void> runReconciliation() {
        adminService.runReconciliation();
        return ResponseEntity.accepted().build();
    }
}
