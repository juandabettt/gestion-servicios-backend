package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.CorrectInvoiceRequest;
import com.tuapp.servicios.application.dto.response.InvoiceResponse;
import com.tuapp.servicios.application.dto.response.UploadInvoiceResponse;
import com.tuapp.servicios.application.exception.BusinessException;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.InvoiceMapper;
import com.tuapp.servicios.application.port.FileStoragePort;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.enums.TipoJob;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.model.Property;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import com.tuapp.servicios.infrastructure.security.FileValidationService;
import com.tuapp.servicios.infrastructure.security.InvoiceOwnershipValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PropertyRepository propertyRepository;
    private final FileStoragePort fileStoragePort;
    private final FileValidationService fileValidationService;
    private final InvoiceOwnershipValidator ownershipValidator;
    private final JobQueueService jobQueueService;
    private final InvoiceMapper invoiceMapper;

    @Transactional
    public UploadInvoiceResponse uploadInvoice(MultipartFile file, UUID propertyId, UUID userId) {
        fileValidationService.validate(file);
        Property property = propertyRepository.findByIdAndUserIdAndDeletedAtIsNull(propertyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Propiedad", propertyId));
        String objectKey = buildObjectKey(userId, propertyId, file.getOriginalFilename());
        try {
            fileStoragePort.upload(objectKey, file.getBytes(), file.getContentType());
        } catch (Exception e) {
            throw new BusinessException("Error al subir el archivo", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        Invoice invoice = Invoice.builder()
                .property(property)
                .estado(EstadoFactura.PROCESANDO_OCR)
                .urlFotoFactura(objectKey)
                .build();
        invoice = invoiceRepository.save(invoice);
        jobQueueService.enqueue(TipoJob.OCR_FACTURA, Map.of(
                "invoiceId", invoice.getId().toString(),
                "objectKey", objectKey));
        log.info("Factura recibida. Job OCR encolado.");
        return UploadInvoiceResponse.builder()
                .invoiceId(invoice.getId())
                .message("Factura recibida. Procesando con OCR...")
                .build();
    }

    @Transactional(readOnly = true)
    public Page<InvoiceResponse> listByUser(UUID userId, Pageable pageable) {
        return invoiceRepository.findByUserId(userId, pageable)
                .map(i -> enrichWithPresignedUrl(invoiceMapper.toResponse(i)));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getById(UUID invoiceId, UUID userId) {
        Invoice invoice = ownershipValidator.validateAndGet(invoiceId, userId);
        return enrichWithPresignedUrl(invoiceMapper.toResponse(invoice));
    }

    @Transactional
    public InvoiceResponse correctOcrData(UUID invoiceId, CorrectInvoiceRequest request, UUID userId) {
        Invoice invoice = ownershipValidator.validateAndGet(invoiceId, userId);
        if (request.getNumeroReferencia() != null) invoice.setNumeroReferencia(request.getNumeroReferencia());
        if (request.getFechaEmision() != null) invoice.setFechaEmision(request.getFechaEmision());
        if (request.getFechaVencimiento() != null) invoice.setFechaVencimiento(request.getFechaVencimiento());
        if (request.getMontoTotal() != null) invoice.setMontoTotal(request.getMontoTotal());
        if (request.getConsumoUnidad() != null) invoice.setConsumoUnidad(request.getConsumoUnidad());
        if (request.getUnidadMedida() != null) invoice.setUnidadMedida(request.getUnidadMedida());
        if (request.getPeriodoFacturado() != null) invoice.setPeriodoFacturado(request.getPeriodoFacturado());
        invoice.setIngresoManual(true);
        if (invoice.getEstado() == EstadoFactura.ERROR_OCR || invoice.getEstado() == EstadoFactura.PROCESANDO_OCR) {
            invoice.setEstado(EstadoFactura.PENDIENTE);
        }
        return enrichWithPresignedUrl(invoiceMapper.toResponse(invoiceRepository.save(invoice)));
    }

    private InvoiceResponse enrichWithPresignedUrl(InvoiceResponse response) {
        if (response.getUrlFotoFactura() != null) {
            try {
                response.setUrlFotoFactura(
                        fileStoragePort.generatePresignedUrl(response.getUrlFotoFactura(), Duration.ofMinutes(15)));
            } catch (Exception e) {
                log.warn("No se pudo generar URL pre-firmada");
            }
        }
        return response;
    }

    private String buildObjectKey(UUID userId, UUID propertyId, String filename) {
        LocalDateTime now = LocalDateTime.now();
        String ext = (filename != null && filename.contains("."))
                ? filename.substring(filename.lastIndexOf('.')) : ".jpg";
        return String.format("%s/%s/%s/%s/%s%s",
                userId, propertyId,
                now.format(DateTimeFormatter.ofPattern("yyyy")),
                now.format(DateTimeFormatter.ofPattern("MM")),
                UUID.randomUUID(), ext);
    }
}
