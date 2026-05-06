package com.tuapp.servicios.infrastructure.scheduler.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.port.OcrServicePort;
import com.tuapp.servicios.application.port.dto.OcrExtractionResult;
import com.tuapp.servicios.application.port.FileStoragePort;
import com.tuapp.servicios.application.service.NotificationService;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OcrJobHandler {

    private final InvoiceRepository invoiceRepository;
    private final OcrServicePort ocrServicePort;
    private final FileStoragePort fileStoragePort;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String payloadJson) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
        UUID invoiceId = UUID.fromString((String) payload.get("invoiceId"));
        String objectKey = (String) payload.get("objectKey");

        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + invoiceId));

        log.info("Procesando OCR para factura");

        String imageUrl = fileStoragePort.generatePresignedUrl(objectKey, Duration.ofMinutes(15));
        log.info("URL de imagen obtenida para OCR");

        OcrExtractionResult result = ocrServicePort.extractInvoiceData(imageUrl.getBytes(StandardCharsets.UTF_8), "url");

        if (result.isExitoso()) {
            boolean confianzaBaja = result.getConfianza() == null
                    || result.getConfianza().compareTo(new BigDecimal("30")) < 0;
            if (confianzaBaja && result.getMontoTotal() == null) {
                log.warn("La imagen no parece ser una factura válida — confianza: {}, montoTotal: null",
                        result.getConfianza());
                invoice.setEstado(EstadoFactura.ERROR_OCR);
            } else {
                invoice.setNumeroReferencia(result.getNumeroReferencia());
                invoice.setFechaEmision(result.getFechaEmision());
                invoice.setFechaVencimiento(result.getFechaVencimiento());
                invoice.setMontoTotal(result.getMontoTotal());
                invoice.setConsumoUnidad(result.getConsumoUnidad());
                invoice.setUnidadMedida(result.getUnidadMedida());
                invoice.setPeriodoFacturado(result.getPeriodoFacturado());
                invoice.setOcrConfianza(result.getConfianza());
                invoice.setOcrDatosRaw(result.getDatosRaw());
                invoice.setEstado(EstadoFactura.PENDIENTE);
                invoiceRepository.save(invoice);
                log.info("OCR completado exitosamente — confianza: {}%", result.getConfianza());
                notificationService.notificarFacturaAgregada(invoice);
                return;
            }
        } else {
            invoice.setEstado(EstadoFactura.ERROR_OCR);
            log.warn("OCR falló: {}", result.getErrorMensaje());
        }

        invoiceRepository.save(invoice);
    }
}
