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
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
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
    private final ObjectMapper objectMapper;

    @Transactional
    public void handle(String payloadJson) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(payloadJson, Map.class);
        UUID invoiceId = UUID.fromString((String) payload.get("invoiceId"));
        String objectKey = (String) payload.get("objectKey");

        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada: " + invoiceId));

        log.info("Procesando OCR para factura");

        byte[] imageBytes = downloadImageBytes(objectKey);
        String mimeType = detectMimeType(objectKey);

        OcrExtractionResult result = ocrServicePort.extractInvoiceData(imageBytes, mimeType);

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
                log.info("OCR completado exitosamente — confianza: {}%", result.getConfianza());
            }
        } else {
            invoice.setEstado(EstadoFactura.ERROR_OCR);
            log.warn("OCR falló: {}", result.getErrorMensaje());
        }

        invoiceRepository.save(invoice);
    }

    private byte[] downloadImageBytes(String objectKey) {
        try {
            String imageUrl = fileStoragePort.generatePresignedUrl(objectKey, Duration.ofMinutes(5));
            byte[] bytes = WebClient.create()
                    .get()
                    .uri(imageUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            if (bytes != null && bytes.length > 0) {
                log.info("Imagen descargada: {} bytes", bytes.length);
                return bytes;
            }
        } catch (Exception e) {
            log.error("Error descargando imagen del storage: {}", e.getMessage());
        }
        return new byte[0];
    }

    private String detectMimeType(String objectKey) {
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }
}
