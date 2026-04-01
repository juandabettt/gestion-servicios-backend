package com.tuapp.servicios.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.dto.response.InvoiceResponse;
import com.tuapp.servicios.application.dto.response.UploadInvoiceResponse;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.application.service.InvoiceService;
import com.tuapp.servicios.domain.enums.EstadoFactura;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.repository.UserRepository;
import com.tuapp.servicios.infrastructure.security.FileValidationService;
import com.tuapp.servicios.infrastructure.security.JwtAuthenticationFilter;
import com.tuapp.servicios.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InvoiceController.class)
class InvoiceControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private InvoiceService invoiceService;
    @MockBean private UserRepository userRepository;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private RedisTemplate<String, String> stringRedisTemplate;

    @Test
    @WithMockUser(username = "user@test.com")
    void upload_withValidFile_returns202() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        User user = User.builder().email("user@test.com").build();
        ReflectionTestUtils.setField(user, "id", userId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "factura.jpg", "image/jpeg", "fake-bytes".getBytes()
        );

        UploadInvoiceResponse response = UploadInvoiceResponse.builder()
                .invoiceId(invoiceId)
                .message("Procesando OCR...")
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(invoiceService.uploadInvoice(any(), eq(propertyId), eq(userId))).thenReturn(response);

        mockMvc.perform(multipart("/api/v1/invoices/upload")
                        .file(file)
                        .param("propertyId", propertyId.toString())
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.invoiceId").value(invoiceId.toString()))
                .andExpect(jsonPath("$.message").value("Procesando OCR..."));
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void upload_withInvalidFileType_returns400() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID propertyId = UUID.randomUUID();

        User user = User.builder().email("user@test.com").build();
        ReflectionTestUtils.setField(user, "id", userId);

        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.exe", "application/x-msdownload", new byte[]{1, 2, 3}
        );

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(invoiceService.uploadInvoice(any(), any(), any()))
                .thenThrow(new FileValidationService.InvalidFileException("Tipo de archivo no permitido"));

        mockMvc.perform(multipart("/api/v1/invoices/upload")
                        .file(file)
                        .param("propertyId", propertyId.toString())
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void list_returnsPagedInvoices() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        User user = User.builder().email("user@test.com").build();
        ReflectionTestUtils.setField(user, "id", userId);

        InvoiceResponse invoice = InvoiceResponse.builder()
                .id(invoiceId)
                .estado(EstadoFactura.PENDIENTE)
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(invoiceService.listByUser(eq(userId), any()))
                .thenReturn(new PageImpl<>(List.of(invoice), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(invoiceId.toString()))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void getById_withExistingInvoice_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        User user = User.builder().email("user@test.com").build();
        ReflectionTestUtils.setField(user, "id", userId);

        InvoiceResponse invoice = InvoiceResponse.builder()
                .id(invoiceId)
                .estado(EstadoFactura.PENDIENTE)
                .urlFotoFactura("https://storage.example.com/facturas/factura.jpg?signature=abc")
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(invoiceService.getById(eq(invoiceId), eq(userId))).thenReturn(invoice);

        mockMvc.perform(get("/api/v1/invoices/{id}", invoiceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(invoiceId.toString()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void getById_withNonExistentInvoice_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        User user = User.builder().email("user@test.com").build();
        ReflectionTestUtils.setField(user, "id", userId);

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(invoiceService.getById(eq(invoiceId), eq(userId)))
                .thenThrow(new ResourceNotFoundException("Factura", invoiceId));

        mockMvc.perform(get("/api/v1/invoices/{id}", invoiceId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }
}
