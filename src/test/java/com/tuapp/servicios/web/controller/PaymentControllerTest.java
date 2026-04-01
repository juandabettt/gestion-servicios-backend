package com.tuapp.servicios.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.dto.request.ProcessPaymentRequest;
import com.tuapp.servicios.application.dto.response.PaymentInitiateResponse;
import com.tuapp.servicios.application.service.PaymentService;
import com.tuapp.servicios.domain.enums.MetodoPago;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.repository.UserRepository;
import com.tuapp.servicios.infrastructure.security.JwtAuthenticationFilter;
import com.tuapp.servicios.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private PaymentService paymentService;
    @MockBean private UserRepository userRepository;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private RedisTemplate<String, String> stringRedisTemplate;

    @Test
    @WithMockUser(username = "user@test.com")
    void processPayment_withValidRequest_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();

        User user = User.builder().email("user@test.com").build();
        ReflectionTestUtils.setField(user, "id", userId);

        ProcessPaymentRequest request = new ProcessPaymentRequest();
        request.setInvoiceId(invoiceId);
        request.setMetodoPago(MetodoPago.TARJETA_CREDITO);
        request.setIdempotencyKey("idem-key-001");

        PaymentInitiateResponse response = PaymentInitiateResponse.builder()
                .transactionId(UUID.randomUUID())
                .estado("APROBADO")
                .message("Pago procesado exitosamente")
                .build();

        when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(paymentService.processPayment(any(), eq(userId))).thenReturn(response);

        mockMvc.perform(post("/api/v1/payments/process")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADO"));
    }

    @Test
    void webhook_withValidHmacSignature_returns200() throws Exception {
        String payload = "{\"event\":\"payment.approved\",\"transactionId\":\"txn-001\"}";
        String validSignature = "sha256=valid-hmac-signature";

        doNothing().when(paymentService).processWebhook(eq(payload), eq(validSignature));

        mockMvc.perform(post("/api/v1/payments/webhook/gateway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Gateway-Signature", validSignature)
                        .content(payload))
                .andExpect(status().isOk());

        verify(paymentService).processWebhook(payload, validSignature);
    }

    @Test
    void webhook_withInvalidHmacSignature_returns401() throws Exception {
        String payload = "{\"event\":\"payment.approved\"}";
        String invalidSignature = "sha256=invalid-signature";

        doThrow(new org.springframework.security.access.AccessDeniedException("Firma HMAC inválida"))
                .when(paymentService).processWebhook(eq(payload), eq(invalidSignature));

        mockMvc.perform(post("/api/v1/payments/webhook/gateway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Gateway-Signature", invalidSignature)
                        .content(payload))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void webhook_withMissingSignatureHeader_returns400() throws Exception {
        String payload = "{\"event\":\"payment.approved\"}";

        mockMvc.perform(post("/api/v1/payments/webhook/gateway")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                // Missing required header should result in 4xx
                .andExpect(status().is4xxClientError());
    }
}
