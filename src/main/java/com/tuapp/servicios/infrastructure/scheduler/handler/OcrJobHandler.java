package com.tuapp.servicios.infrastructure.scheduler.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.port.OcrServicePort;
import com.tuapp.servicios.application.port.dto.OcrExtractionResult;
import com.tuapp.servicios.application.port.FileStoragePort;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OcrJobHandler {

    private final InvoiceRepository invoiceRepository;
    private final OcrServicePort ocrServicePort;
    private final FileStoragePort fileStoragePort;
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String payloadJson) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
        UUID invoiceId = UUID.fromString((String) payload.get("invoiceId"));
        String objectKey = (String) payload.get("objectKey");

        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + invoiceId));

        log.info("Procesando OCR para factura");

        // Descargar imagen para procesarla (en mock simplemente pasamos bytes vacíos)
        byte[] imageBytes;
        try {
            // En producción se descargaría del storage; el mock no necesita bytes reales
            imageBytes = new byte[0];
        } catch (Exception e) {
            log.error("Error descargando imagen del storage");
            imageBytes = new byte[0];
        }

        OcrExtractionResult result = ocrServicePort.extractInvoiceData(imageBytes, "image/jpeg");

        if (result.isExitoso()) {
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
            log.info("OCR completado exitosamente — confianza: {}%", result.getConfianza());
        } else {
            invoice.setEstado(EstadoFactura.ERROR_OCR);
            log.warn("OCR falló: {}", result.getErrorMensaje());
        }

        invoiceRepository.save(invoice);
    }
}
