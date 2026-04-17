package com.tuapp.servicios.web.controller;

import com.tuapp.servicios.application.dto.response.NotificationResponse;
import com.tuapp.servicios.application.dto.response.UserPreferencesResponse;
import com.tuapp.servicios.application.dto.request.UpdatePreferencesRequest;
import com.tuapp.servicios.application.service.NotificationService;
import com.tuapp.servicios.application.service.UserPreferencesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import com.tuapp.servicios.domain.repository.UserRepository;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notificaciones", description = "Gestión de notificaciones y preferencias del usuario")
public class NotificationController {

    private final NotificationService notificationService;
    private final UserPreferencesService preferencesService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Listar notificaciones del usuario (paginadas)")
    public ResponseEntity<Page<NotificationResponse>> list(
            @RequestParam(defaultValue = "false") boolean soloNoLeidas,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(notificationService.getByUsuario(userId, soloNoLeidas, PageRequest.of(page, size)));
    }

    @PutMapping("/{id}/leer")
    @Operation(summary = "Marcar notificación como leída")
    public ResponseEntity<Void> marcarLeida(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        notificationService.marcarLeida(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/leer-todas")
    @Operation(summary = "Marcar todas las notificaciones como leídas")
    public ResponseEntity<Void> marcarTodasLeidas(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        notificationService.marcarTodasLeidas(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    @Operation(summary = "Obtener preferencias de notificación")
    public ResponseEntity<UserPreferencesResponse> getPreferences(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(preferencesService.get(userId));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Actualizar preferencias de notificación")
    public ResponseEntity<UserPreferencesResponse> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(preferencesService.update(request, userId));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
    }
}
