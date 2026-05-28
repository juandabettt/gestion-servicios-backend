package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.response.AiAnalysisResponse;
import com.tuapp.servicios.application.dto.response.AiAnalyzeResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.AiAnalysisMapper;
import com.tuapp.servicios.application.port.dto.AiAnalysisPortResult;
import com.tuapp.servicios.application.port.dto.ConsumptionHistoryContext;
import com.tuapp.servicios.domain.enums.EstadoAnalisis;
import com.tuapp.servicios.domain.enums.TipoAnalisis;
import com.tuapp.servicios.domain.enums.TipoJob;
import com.tuapp.servicios.domain.enums.TipoServicio;
import com.tuapp.servicios.domain.model.AiAnalysis;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.model.Property;
import com.tuapp.servicios.domain.repository.AiAnalysisRepository;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiInsightsService {

    private final AiAnalysisRepository aiAnalysisRepository;
    private final PropertyRepository propertyRepository;
    private final InvoiceRepository invoiceRepository;
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

    @Transactional(readOnly = true)
    public AiAnalysisResponse getPrediction(UUID propertyId, TipoServicio tipoServicio, UUID userId) {
        propertyService.validateOwnership(propertyId, userId);

        Optional<AiAnalysis> prediction = aiAnalysisRepository
            .findFirstByPropertyIdAndTipoServicioAndTipoAnalisisAndEstadoOrderByCreatedAtDesc(
                propertyId, tipoServicio, TipoAnalisis.PREDICCION, EstadoAnalisis.COMPLETADO);

        if (prediction.isPresent()) {
            return aiAnalysisMapper.toResponse(prediction.get());
        }

        AiAnalysis empty = AiAnalysis.builder()
            .property(propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad", propertyId)))
            .tipoAnalisis(TipoAnalisis.PREDICCION)
            .tipoServicio(tipoServicio)
            .descripcion("Sin datos suficientes para generar una predicción. " +
                        "Necesitas al menos 3 facturas históricas.")
            .estado(EstadoAnalisis.COMPLETADO)
            .build();

        return aiAnalysisMapper.toResponse(aiAnalysisRepository.save(empty));
    }

    @Transactional(readOnly = true)
    public AiAnalysisResponse getBenchmark(UUID propertyId, TipoServicio tipoServicio, UUID userId) {
        propertyService.validateOwnership(propertyId, userId);

        Optional<AiAnalysis> benchmark = aiAnalysisRepository
            .findFirstByPropertyIdAndTipoServicioAndTipoAnalisisAndEstadoOrderByCreatedAtDesc(
                propertyId, tipoServicio, TipoAnalisis.COMPARATIVA, EstadoAnalisis.COMPLETADO);

        if (benchmark.isPresent()) {
            return aiAnalysisMapper.toResponse(benchmark.get());
        }

        AiAnalysis empty = AiAnalysis.builder()
            .property(propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad", propertyId)))
            .tipoAnalisis(TipoAnalisis.COMPARATIVA)
            .tipoServicio(tipoServicio)
            .descripcion("Sin datos suficientes para comparativa. " +
                        "Se necesitan al menos 5 hogares en tu zona.")
            .estado(EstadoAnalisis.COMPLETADO)
            .build();

        return aiAnalysisMapper.toResponse(aiAnalysisRepository.save(empty));
    }

    @Transactional
    public AiAnalysisResponse submitFeedback(UUID analysisId, Integer calificacion, UUID userId) {
        AiAnalysis analysis = aiAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new ResourceNotFoundException("Análisis", analysisId));
        propertyService.validateOwnership(analysis.getProperty().getId(), userId);
        analysis.setCalificacionUsuario(calificacion);
        return aiAnalysisMapper.toResponse(aiAnalysisRepository.save(analysis));
    }

    @Transactional(readOnly = true)
    public AiAnalysisResponse calculateInstantAnalysis(UUID propertyId, UUID userId) {
        propertyService.validateOwnership(propertyId, userId);

        List<Invoice> invoices = invoiceRepository.findTop6ByPropertyIdOrderByFechaVencimientoDesc(propertyId);

        if (invoices.isEmpty()) {
            return AiAnalysisResponse.builder()
                    .descripcion("Sin facturas para analizar. Carga al menos una factura para ver análisis.")
                    .estado(EstadoAnalisis.COMPLETADO)
                    .build();
        }

        List<ConsumptionHistoryContext.ConsumoMensual> consumoHistorico = invoices.stream()
                .map(inv -> ConsumptionHistoryContext.ConsumoMensual.builder()
                        .periodo(inv.getFechaVencimiento() != null ? inv.getFechaVencimiento().toString() : "")
                        .consumoUnidad(inv.getConsumoUnidad())
                        .montoTotal(inv.getMontoTotal())
                        .build())
                .collect(Collectors.toList());

        List<Invoice> conMonto = invoices.stream()
                .filter(inv -> inv.getMontoTotal() != null)
                .collect(Collectors.toList());

        if (conMonto.isEmpty()) {
            return AiAnalysisResponse.builder()
                    .descripcion("Facturas encontradas pero sin monto registrado aún.")
                    .estado(EstadoAnalisis.COMPLETADO)
                    .consumoHistorico(consumoHistorico)
                    .build();
        }

        BigDecimal sumaMontos = conMonto.stream()
                .map(Invoice::getMontoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedio = sumaMontos.divide(BigDecimal.valueOf(conMonto.size()), 2, RoundingMode.HALF_UP);

        Invoice maxMonth = conMonto.stream()
                .max(Comparator.comparing(Invoice::getMontoTotal))
                .orElse(conMonto.get(0));

        BigDecimal sumaConsumo = invoices.stream()
                .map(Invoice::getConsumoUnidad)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedioConsumo = sumaConsumo.compareTo(BigDecimal.ZERO) > 0
                ? sumaConsumo.divide(BigDecimal.valueOf(invoices.size()), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        AiAnalysisPortResult.ConsumptionPrediction prediccion = AiAnalysisPortResult.ConsumptionPrediction.builder()
                .montoEstimado(promedio)
                .consumoEstimado(promedioConsumo)
                .rangoBajo(promedio.multiply(new BigDecimal("0.9")).setScale(2, RoundingMode.HALF_UP))
                .rangoAlto(promedio.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_UP))
                .factores(List.of("Promedio de últimas " + conMonto.size() + " facturas",
                        "Mes de mayor gasto: " + (maxMonth.getFechaVencimiento() != null
                                ? maxMonth.getFechaVencimiento().toString() : "N/A")))
                .build();

        String descripcion = String.format(
                "Análisis instantáneo: promedio mensual $%.2f basado en %d facturas. Mayor gasto: $%.2f.",
                promedio, conMonto.size(), maxMonth.getMontoTotal());

        return AiAnalysisResponse.builder()
                .tipoAnalisis(TipoAnalisis.PREDICCION)
                .descripcion(descripcion)
                .estado(EstadoAnalisis.COMPLETADO)
                .consumoHistorico(consumoHistorico)
                .prediccion(prediccion)
                .build();
    }
}
