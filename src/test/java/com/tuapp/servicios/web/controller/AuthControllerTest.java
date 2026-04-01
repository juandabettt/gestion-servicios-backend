package com.tuapp.servicios.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuapp.servicios.application.dto.request.LoginRequest;
import com.tuapp.servicios.application.dto.request.RegisterRequest;
import com.tuapp.servicios.application.dto.response.AuthResponse;
import com.tuapp.servicios.application.exception.DuplicateResourceException;
import com.tuapp.servicios.application.service.AuthService;
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
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private AuthService authService;
    @MockBean private UserRepository userRepository;
    @MockBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockBean private JwtTokenProvider jwtTokenProvider;
    @MockBean private UserDetailsService userDetailsService;
    @MockBean private RedisTemplate<String, String> stringRedisTemplate;

    @Test
    void register_withValidData_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Juan Test");
        request.setEmail("juan@test.com");
        request.setPassword("password123");

        AuthResponse response = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .userId(UUID.randomUUID())
                .nombre("Juan Test")
                .rol("USER")
                .build();

        when(authService.register(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.rol").value("USER"));
    }

    @Test
    void register_withExistingEmail_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Test");
        request.setEmail("existing@test.com");
        request.setPassword("password123");

        when(authService.register(any()))
                .thenThrow(new DuplicateResourceException("El email ya está registrado"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void login_withValidCredentials_returns200() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("juan@test.com");
        request.setPassword("password123");

        AuthResponse response = AuthResponse.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .tokenType("Bearer")
                .userId(UUID.randomUUID())
                .nombre("Juan")
                .rol("USER")
                .build();

        when(authService.login(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-token"));
    }

    @Test
    void register_withMissingFields_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        // email y nombre faltantes

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
