package com.tuapp.servicios.infrastructure.scheduler.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.port.AiAnalysisPort;
import com.tuapp.servicios.application.port.dto.AiAnalysisPortResult;
import com.tuapp.servicios.application.port.dto.ConsumptionHistoryContext;
import com.tuapp.servicios.application.service.AiPromptBuilderService;
import com.tuapp.servicios.application.service.NotificationService;
import com.tuapp.servicios.domain.enums.EstadoAnalisis;
import com.tuapp.servicios.domain.enums.TipoAnalisis;
import com.tuapp.servicios.domain.enums.TipoServicio;
import com.tuapp.servicios.domain.model.AiAnalysis;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.repository.AiAnalysisRepository;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiAnalysisJobHandler {

    private final AiAnalysisRepository aiAnalysisRepository;
    private final InvoiceRepository invoiceRepository;
    private final AiAnalysisPort aiAnalysisPort;
    private final AiPromptBuilderService promptBuilder;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String payloadJson) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
        UUID analysisId = UUID.fromString((String) payload.get("analysisId"));
        UUID propertyId = UUID.fromString((String) payload.get("propertyId"));
        TipoServicio tipoServicio = TipoServicio.valueOf((String) payload.get("tipoServicio"));

        AiAnalysis analysis = aiAnalysisRepository.findById(analysisId)
                .orElseThrow(() -> new RuntimeException("Análisis no encontrado: " + analysisId));

        // Construir historial de consumo (últimas 12 facturas pagadas del proveedor)
        List<Invoice> historial = invoiceRepository.findHistorialByPropertyAndProveedor(
                propertyId, null, org.springframework.data.domain.PageRequest.of(0, 12));

        List<ConsumptionHistoryContext.ConsumoMensual> consumoMensual = historial.stream()
                .filter(i -> i.getConsumoUnidad() != null && i.getMontoTotal() != null)
                .map(i -> ConsumptionHistoryContext.ConsumoMensual.builder()
                        .periodo(i.getPeriodoFacturado())
                        .consumoUnidad(i.getConsumoUnidad())
                        .montoTotal(i.getMontoTotal())
                        .build())
                .collect(Collectors.toList());

        ConsumptionHistoryContext context = ConsumptionHistoryContext.builder()
                .propertyId(propertyId)
                .tipoServicio(tipoServicio)
                .historial(consumoMensual)
                .build();

        analysis.setDatosEntrada(objectMapper.writeValueAsString(context));

        AiAnalysisPortResult result = aiAnalysisPort.analyzeConsumption(context);

        if (result.isExitoso()) {
            analysis.setDescripcion(result.getResumenEjecutivo());
            analysis.setResultadoJson(objectMapper.writeValueAsString(result));
            analysis.setEstado(EstadoAnalisis.COMPLETADO);
            analysis.setTipoAnalisis(TipoAnalisis.RECOMENDACION);

            // Notificar al usuario
            notificationService.notificarAnalisisListo(
                    analysis.getProperty().getUser(), analysis);

            log.info("Análisis IA completado exitosamente");
        } else {
            analysis.setEstado(EstadoAnalisis.FALLIDO);
            log.warn("Análisis IA fallido: {}", result.getErrorMensaje());
        }

        aiAnalysisRepository.save(analysis);
    }
}
