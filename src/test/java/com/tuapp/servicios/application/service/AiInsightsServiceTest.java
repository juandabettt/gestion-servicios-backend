package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.response.AiAnalysisResponse;
import com.tuapp.servicios.application.dto.response.AiAnalyzeResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.AiAnalysisMapper;
import com.tuapp.servicios.domain.enums.*;
import com.tuapp.servicios.domain.model.*;
import com.tuapp.servicios.domain.repository.AiAnalysisRepository;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiInsightsServiceTest {

    @Mock private AiAnalysisRepository aiAnalysisRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private JobQueueService jobQueueService;
    @Mock private PropertyService propertyService;
    @Mock private AiAnalysisMapper aiAnalysisMapper;

    @InjectMocks
    private AiInsightsService aiInsightsService;

    @Test
    void requestAnalysis_withRecentCompletedAnalysis_returnsCachedResult() {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();

        AiAnalysis recentAnalysis = AiAnalysis.builder()
                .estado(EstadoAnalisis.COMPLETADO)
                .tipoServicio(TipoServicio.ENERGIA)
                .build();
        ReflectionTestUtils.setField(recentAnalysis, "id", analysisId);

        doNothing().when(propertyService).validateOwnership(propertyId, userId);
        when(aiAnalysisRepository.findFirstByPropertyIdAndTipoServicioAndCreatedAtAfterOrderByCreatedAtDesc(
                eq(propertyId), eq(TipoServicio.ENERGIA), any()))
                .thenReturn(Optional.of(recentAnalysis));

        AiAnalyzeResponse response = aiInsightsService.requestAnalysis(propertyId, TipoServicio.ENERGIA, userId);

        assertThat(response.getAnalysisId()).isEqualTo(analysisId);
        assertThat(response.getMessage()).contains("caché");

        verifyNoInteractions(jobQueueService);
        verifyNoInteractions(propertyRepository);
    }

    @Test
    void requestAnalysis_withNoRecentAnalysis_enqueuedNewJob() {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID newAnalysisId = UUID.randomUUID();

        User user = User.builder().build();
        Property property = Property.builder().user(user).nombre("Casa").build();
        ReflectionTestUtils.setField(property, "id", propertyId);

        AiAnalysis savedAnalysis = AiAnalysis.builder()
                .property(property).tipoServicio(TipoServicio.AGUA)
                .estado(EstadoAnalisis.PROCESANDO).build();
        ReflectionTestUtils.setField(savedAnalysis, "id", newAnalysisId);

        doNothing().when(propertyService).validateOwnership(propertyId, userId);
        when(aiAnalysisRepository.findFirstByPropertyIdAndTipoServicioAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any()))
                .thenReturn(Optional.empty());
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(aiAnalysisRepository.save(any())).thenReturn(savedAnalysis);

        AiAnalyzeResponse response = aiInsightsService.requestAnalysis(propertyId, TipoServicio.AGUA, userId);

        assertThat(response.getAnalysisId()).isEqualTo(newAnalysisId);
        assertThat(response.getMessage()).contains("Análisis iniciado");

        verify(jobQueueService).enqueue(
                eq(TipoJob.ANALISIS_IA),
                argThat(map -> map.containsKey("analysisId")
                        && map.containsKey("propertyId")
                        && map.containsKey("tipoServicio"))
        );
    }

    @Test
    void requestAnalysis_withProcessingAnalysis_enqueuedNewJob() {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        // A PROCESANDO analysis (not COMPLETADO) should trigger a new job
        AiAnalysis processingAnalysis = AiAnalysis.builder()
                .estado(EstadoAnalisis.PROCESANDO)
                .tipoServicio(TipoServicio.GAS)
                .build();
        ReflectionTestUtils.setField(processingAnalysis, "id", UUID.randomUUID());

        User user = User.builder().build();
        Property property = Property.builder().user(user).nombre("Apt").build();
        ReflectionTestUtils.setField(property, "id", propertyId);

        AiAnalysis savedAnalysis = AiAnalysis.builder()
                .property(property).tipoServicio(TipoServicio.GAS)
                .estado(EstadoAnalisis.PROCESANDO).build();
        ReflectionTestUtils.setField(savedAnalysis, "id", UUID.randomUUID());

        doNothing().when(propertyService).validateOwnership(propertyId, userId);
        when(aiAnalysisRepository.findFirstByPropertyIdAndTipoServicioAndCreatedAtAfterOrderByCreatedAtDesc(
                any(), any(), any()))
                .thenReturn(Optional.of(processingAnalysis));
        when(propertyRepository.findById(propertyId)).thenReturn(Optional.of(property));
        when(aiAnalysisRepository.save(any())).thenReturn(savedAnalysis);

        AiAnalyzeResponse response = aiInsightsService.requestAnalysis(propertyId, TipoServicio.GAS, userId);

        assertThat(response.getMessage()).contains("iniciado");
        verify(jobQueueService).enqueue(eq(TipoJob.ANALISIS_IA), any());
    }

    @Test
    void submitFeedback_withValidAnalysis_updatesCalificacion() {
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        User user = User.builder().build();
        Property property = Property.builder().user(user).build();
        ReflectionTestUtils.setField(property, "id", propertyId);

        AiAnalysis analysis = AiAnalysis.builder()
                .property(property).estado(EstadoAnalisis.COMPLETADO).build();
        ReflectionTestUtils.setField(analysis, "id", analysisId);

        AiAnalysisResponse mockResponse = AiAnalysisResponse.builder()
                .id(analysisId).calificacionUsuario(5).build();

        when(aiAnalysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));
        doNothing().when(propertyService).validateOwnership(propertyId, userId);
        when(aiAnalysisRepository.save(any())).thenReturn(analysis);
        when(aiAnalysisMapper.toResponse(any())).thenReturn(mockResponse);

        AiAnalysisResponse response = aiInsightsService.submitFeedback(analysisId, 5, userId);

        assertThat(analysis.getCalificacionUsuario()).isEqualTo(5);
        assertThat(response.getCalificacionUsuario()).isEqualTo(5);
    }

    @Test
    void submitFeedback_withNonExistentAnalysis_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID analysisId = UUID.randomUUID();

        when(aiAnalysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiInsightsService.submitFeedback(analysisId, 4, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
