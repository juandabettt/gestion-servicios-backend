package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.response.UploadInvoiceResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.mapper.InvoiceMapper;
import com.tuapp.servicios.application.port.FileStoragePort;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.model.*;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import com.tuapp.servicios.domain.repository.PropertyRepository;
import com.tuapp.servicios.infrastructure.security.FileValidationService;
import com.tuapp.servicios.infrastructure.security.InvoiceOwnershipValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private FileStoragePort fileStoragePort;
    @Mock private FileValidationService fileValidationService;
    @Mock private InvoiceOwnershipValidator ownershipValidator;
    @Mock private JobQueueService jobQueueService;
    @Mock private InvoiceMapper invoiceMapper;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void uploadInvoice_withValidFile_returns202ResponseWithJobEnqueued() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file", "factura.jpg", "image/jpeg", "fake-image-bytes".getBytes()
        );

        User user = User.builder().nombre("Test").build();
        ReflectionTestUtils.setField(user, "id", userId);

        Property property = Property.builder().user(user).nombre("Casa").build();
        ReflectionTestUtils.setField(property, "id", propertyId);

        Invoice savedInvoice = Invoice.builder()
                .property(property).estado(EstadoFactura.PROCESANDO_OCR).build();
        UUID invoiceId = UUID.randomUUID();
        ReflectionTestUtils.setField(savedInvoice, "id", invoiceId);

        doNothing().when(fileValidationService).validate(any());
        when(propertyRepository.findByIdAndUserIdAndDeletedAtIsNull(propertyId, userId))
                .thenReturn(Optional.of(property));
        when(fileStoragePort.upload(any(), any(), any())).thenReturn("storage-key");
        when(invoiceRepository.save(any())).thenReturn(savedInvoice);

        UploadInvoiceResponse response = invoiceService.uploadInvoice(file, propertyId, userId);

        assertThat(response.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(response.getMessage()).contains("Procesando");

        verify(fileValidationService).validate(file);
        verify(fileStoragePort).upload(any(), any(), any());
        verify(jobQueueService).enqueue(
                eq(com.tuapp.servicios.domain.enums.TipoJob.OCR_FACTURA),
                argThat(map -> map.containsKey("invoiceId") && map.containsKey("objectKey"))
        );
    }

    @Test
    void uploadInvoice_withInvalidProperty_throwsNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[]{1, 2, 3}
        );

        doNothing().when(fileValidationService).validate(any());
        when(propertyRepository.findByIdAndUserIdAndDeletedAtIsNull(propertyId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.uploadInvoice(file, propertyId, userId))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(fileStoragePort, invoiceRepository, jobQueueService);
    }

    @Test
    void uploadInvoice_withInvalidFile_throwsFileValidationException() {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "test.exe", "application/x-msdownload", new byte[]{});

        doThrow(new FileValidationService.InvalidFileException("Tipo de archivo no permitido"))
                .when(fileValidationService).validate(file);

        assertThatThrownBy(() -> invoiceService.uploadInvoice(file, propertyId, userId))
                .isInstanceOf(FileValidationService.InvalidFileException.class)
                .hasMessageContaining("no permitido");

        verifyNoInteractions(propertyRepository, invoiceRepository);
    }

    @Test
    void correctOcrData_withManualInput_setsIngresoManualTrue() {
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", userId);
        Property property = Property.builder().user(user).build();

        Invoice invoice = Invoice.builder()
                .property(property).estado(EstadoFactura.ERROR_OCR).build();
        ReflectionTestUtils.setField(invoice, "id", invoiceId);

        var request = new com.tuapp.servicios.application.dto.request.CorrectInvoiceRequest();
        request.setNumeroReferencia("REF-MANUAL-001");
        request.setMontoTotal(java.math.BigDecimal.valueOf(150000));

        when(ownershipValidator.validateAndGet(invoiceId, userId)).thenReturn(invoice);
        when(invoiceRepository.save(any())).thenReturn(invoice);
        when(invoiceMapper.toResponse(any())).thenReturn(
                com.tuapp.servicios.application.dto.response.InvoiceResponse.builder()
                        .id(invoiceId).estado(EstadoFactura.PENDIENTE).ingresoManual(true).build()
        );

        var response = invoiceService.correctOcrData(invoiceId, request, userId);

        assertThat(invoice.getIngresoManual()).isTrue();
        assertThat(invoice.getEstado()).isEqualTo(EstadoFactura.PENDIENTE);
        assertThat(invoice.getNumeroReferencia()).isEqualTo("REF-MANUAL-001");
    }
}
