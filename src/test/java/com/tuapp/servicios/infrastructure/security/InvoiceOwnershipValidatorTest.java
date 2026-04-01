package com.tuapp.servicios.infrastructure.security;

import com.tuapp.servicios.application.service.AuditService;
import com.tuapp.servicios.domain.model.*;
import com.tuapp.servicios.domain.repository.InvoiceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvoiceOwnershipValidatorTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private AuditService auditService;

    @InjectMocks
    private InvoiceOwnershipValidator validator;

    @Test
    void validateAndGet_withCorrectOwner_returnsInvoice() {
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", userId);

        Property property = Property.builder().user(user).build();
        Invoice invoice = Invoice.builder().property(property).build();
        ReflectionTestUtils.setField(invoice, "id", invoiceId);

        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));

        Invoice result = validator.validateAndGet(invoiceId, userId);

        assertThat(result).isNotNull();
        assertThat(result).isSameAs(invoice);
    }

    @Test
    void validateAndGet_withDifferentOwner_throwsAccessDeniedException() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID actualOwnerId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        User actualOwner = User.builder().build();
        ReflectionTestUtils.setField(actualOwner, "id", actualOwnerId);

        Property property = Property.builder().user(actualOwner).build();
        Invoice invoice = Invoice.builder().property(property).build();
        ReflectionTestUtils.setField(invoice, "id", invoiceId);

        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> validator.validateAndGet(invoiceId, authenticatedUserId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("permiso");

        // Verifica que se registró el intento en auditoría
        verify(auditService).log(eq(authenticatedUserId), eq("UNAUTHORIZED_ACCESS_ATTEMPT"),
                eq("Invoice"), eq(invoiceId), any(), any());
    }

    @Test
    void validateAndGet_withNonExistentInvoice_throwsResourceNotFoundException() {
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        when(invoiceRepository.findByIdAndDeletedAtIsNull(invoiceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> validator.validateAndGet(invoiceId, userId))
                .isInstanceOf(InvoiceOwnershipValidator.ResourceNotFoundException.class);
    }
}
