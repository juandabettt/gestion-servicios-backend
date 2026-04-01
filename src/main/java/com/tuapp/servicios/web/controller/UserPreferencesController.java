package com.tuapp.servicios.web.controller;

import com.tuapp.servicios.application.dto.request.UpdatePreferencesRequest;
import com.tuapp.servicios.application.dto.response.UserPreferencesResponse;
import com.tuapp.servicios.application.service.UserPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.tuapp.servicios.domain.repository.UserRepository;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/preferences")
@RequiredArgsConstructor
@Tag(name = "Preferencias", description = "Preferencias del usuario (presupuestos, método de pago default)")
public class UserPreferencesController {

    private final UserPreferencesService preferencesService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Obtener preferencias del usuario")
    public ResponseEntity<UserPreferencesResponse> get(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(preferencesService.get(resolveUserId(userDetails)));
    }

    @PutMapping
    @Operation(summary = "Actualizar preferencias (presupuestos, método de pago default, etc.)")
    public ResponseEntity<UserPreferencesResponse> update(
            @Valid @RequestBody UpdatePreferencesRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(preferencesService.update(request, resolveUserId(userDetails)));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
    }
}
