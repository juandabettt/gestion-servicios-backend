package com.tuapp.servicios.web.controller;

import com.tuapp.servicios.application.dto.request.CreatePropertyRequest;
import com.tuapp.servicios.application.dto.request.UpdatePropertyRequest;
import com.tuapp.servicios.application.dto.response.PropertyResponse;
import com.tuapp.servicios.application.service.PropertyService;
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
@RequestMapping("/api/v1/properties")
@RequiredArgsConstructor
@Tag(name = "Propiedades", description = "Gestión de inmuebles/hogares del usuario")
public class PropertyController {

    private final PropertyService propertyService;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Crear nueva propiedad")
    public ResponseEntity<PropertyResponse> create(
            @Valid @RequestBody CreatePropertyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.status(HttpStatus.CREATED).body(propertyService.create(request, userId));
    }

    @GetMapping
    @Operation(summary = "Listar propiedades del usuario autenticado")
    public ResponseEntity<Page<PropertyResponse>> list(
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(propertyService.listByUser(userId, pageable));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar una propiedad")
    public ResponseEntity<PropertyResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePropertyRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(propertyService.update(id, request, userId));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar una propiedad (soft delete)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        propertyService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
    }
}
