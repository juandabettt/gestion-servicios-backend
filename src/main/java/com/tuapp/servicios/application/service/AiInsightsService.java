package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.response.AiAnalysisResponse;
import com.tuapp.servicios.application.dto.response.AiAnalyzeResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.AiAnalysisMapper;
import com.tuapp.servicios.domain.enums.EstadoAnalisis;
import com.tuapp.servicios.domain.enums.TipoAnalisis;
import com.tuapp.servicios.domain.enums.TipoJob;
import com.tuapp.servicios.domain.enums.TipoServicio;
import com.tuapp.servicios.domain.model.AiAnalysis;
import com.tuapp.servicios.domain.model.Property;
import com.tuapp.servicios.domain.repository.AiAnalysisRepository;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInsightsService {

    private final AiAnalysisRepository aiAnalysisRepository;
    private final PropertyRepository propertyRepository;
    private final JobQueueService jobQueueService;
    private final PropertyService propertyService;
    private final AiAnalysisMapper aiAnalysisMapper;

    @Transactional
    public AiAnalyzeResponse requestAnalysis(UUID propertyId, TipoServicio tipoServicio, UUID userId) {
        propertyService.validateOwnership(propertyId, userId);
        Optional<AiAnalysis> reciente = aiAnalysisRepository
                .findFirstByPropertyIdAndTipoServicioAndCreatedAtAfterOrderByCreatedAtDesc(
                        propertyId, tipoServicio, LocalDateTime.now().minusDays(7));
        if (reciente.isPresent() && reciente.get().getEstado() == EstadoAnalisis.COMPLETADO) {
            return AiAnalyzeResponse.builder()
                    .analysisId(reciente.get().getId())
                    .message("Análisis reciente disponible (caché de 7 días).")
                    .build();
        }
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad", propertyId));
        AiAnalysis analysis = AiAnalysis.builder()
                .property(property).tipoAnalisis(TipoAnalisis.ANOMALIA)
                .tipoServicio(tipoServicio).descripcion("Análisis en proceso...")
                .estado(EstadoAnalisis.PROCESANDO).build();
        analysis = aiAnalysisRepository.save(analysis);
        jobQueueService.enqueue(TipoJob.ANALISIS_IA, Map.of(
                "analysisId", analysis.getId().toString(),
                "propertyId", propertyId.toString(),
                "tipoServicio", tipoServicio.name()));
        return AiAnalyzeResponse.builder()
                .analysisId(analysis.getId())
                .message("Análisis iniciado. Recibirás una notificación cuando esté listo.")
                .build();
    }

    @Transactional(readOnly = true)
    public Page<AiAnalysisResponse> listByProperty(UUID propertyId, UUID userId, Pageable pageable) {
        propertyService.validateOwnership(propertyId, userId);
        return aiAnalysisRepository.findByPropertyIdAndEstadoOrderByCreatedAtDesc(
                propertyId, EstadoAnalisis.COMPLETADO, pageable).map(aiAnalysisMapper::toResponse);
    }

    @Transactional
    public AiAnalysisResponse submitFeedback(UUID analysisId, Integer calificacion, UUID userId) {
        AiAnalysis analysis = aiAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Análisis", analysisId));
        propertyService.validateOwnership(analysis.getProperty().getId(), userId);
        analysis.setCalificacionUsuario(calificacion);
        return aiAnalysisMapper.toResponse(aiAnalysisRepository.save(analysis));
    }
}
