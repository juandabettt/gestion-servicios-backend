package com.tuapp.servicios.web.controller;

import com.tuapp.servicios.application.dto.request.AiFeedbackRequest;
import com.tuapp.servicios.application.dto.response.AiAnalysisResponse;
import com.tuapp.servicios.application.dto.response.AiAnalyzeResponse;
import com.tuapp.servicios.application.service.AiInsightsService;
import com.tuapp.servicios.domain.enums.TipoServicio;
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
@RequestMapping("/api/v1/ai-insights")
@RequiredArgsConstructor
@Tag(name = "Inteligencia Artificial", description = "Análisis de consumo, anomalías, predicciones y recomendaciones")
public class AiInsightsController {

    private final AiInsightsService aiInsightsService;
    private final UserRepository userRepository;

    @PostMapping("/analyze")
    @Operation(summary = "Solicitar análisis IA del historial de consumo (asíncrono)")
    public ResponseEntity<AiAnalyzeResponse> analyze(
            @RequestParam UUID propertyId,
            @RequestParam(defaultValue = "TODOS") TipoServicio tipoServicio,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(aiInsightsService.requestAnalysis(propertyId, tipoServicio, userId));
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Listar análisis y recomendaciones previas (paginado)")
    public ResponseEntity<Page<AiAnalysisResponse>> list(
            @RequestParam UUID propertyId,
            @PageableDefault(size = 10) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(aiInsightsService.listByProperty(propertyId, userId, pageable));
    }

    @PostMapping("/{id}/feedback")
    @Operation(summary = "Calificar un análisis IA (1-5)")
    public ResponseEntity<AiAnalysisResponse> feedback(
            @PathVariable UUID id,
            @Valid @RequestBody AiFeedbackRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = resolveUserId(userDetails);
        return ResponseEntity.ok(aiInsightsService.submitFeedback(id, request.getCalificacion(), userId));
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername()).orElseThrow().getId();
    }
}
