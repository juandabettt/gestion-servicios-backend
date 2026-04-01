package com.tuapp.servicios.infrastructure.security;

import com.tuapp.servicios.application.service.AuditService;
import com.tuapp.servicios.domain.model.Invoice;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoiceOwnershipValidator {

    private final InvoiceRepository invoiceRepository;
    private final AuditService auditService;

    public Invoice validateAndGet(UUID invoiceId, UUID authenticatedUserId) {
        Invoice invoice = invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Factura no encontrada: " + invoiceId));

        if (!invoice.getProperty().getUser().getId().equals(authenticatedUserId)) {
            auditService.log(authenticatedUserId, "UNAUTHORIZED_ACCESS_ATTEMPT", "Invoice",
                    invoiceId, com.tuapp.servicios.domain.enums.ResultadoAudit.FALLO,
                    "Intento de acceso a factura de otro usuario");
            throw new AccessDeniedException("No tienes permiso para acceder a esta factura");
        }
        return invoice;
    }

    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }
}
