package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.LoginRequest;
import com.tuapp.servicios.application.dto.request.RegisterRequest;
import com.tuapp.servicios.application.dto.response.AuthResponse;
import com.tuapp.servicios.application.exception.DuplicateResourceException;
import com.tuapp.servicios.domain.enums.RolUsuario;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.model.UserPreferences;
import com.tuapp.servicios.domain.model.RefreshToken;
import com.tuapp.servicios.domain.repository.RefreshTokenRepository;
import com.tuapp.servicios.domain.repository.UserPreferencesRepository;
import com.tuapp.servicios.domain.repository.UserRepository;
import com.tuapp.servicios.infrastructure.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private UserPreferencesRepository preferencesRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private AuditService auditService;
    @Mock private RedisTemplate<String, String> stringRedisTemplate;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "refreshExpirationMs", 604800000L);
    }

    @Test
    void register_withNewEmail_returnsAuthResponse() {
        RegisterRequest request = new RegisterRequest();
        request.setNombre("Juan Pérez");
        request.setEmail("juan@example.com");
        request.setPassword("password123");

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        when(userRepository.save(any())).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            ReflectionTestUtils.setField(u, "id", UUID.randomUUID());
            return u;
        });
        when(preferencesRepository.save(any())).thenReturn(new UserPreferences());
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("mock-access-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.register(request);

        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_withExistingEmail_throwsDuplicateException() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("existing@example.com");
        request.setPassword("pass");
        request.setNombre("Test");

        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("ya está registrado");
    }

    @Test
    void login_withValidCredentials_returnsAuthResponse() {
        LoginRequest request = new LoginRequest();
        request.setEmail("juan@example.com");
        request.setPassword("password");

        User user = User.builder()
                .nombre("Juan").email("juan@example.com")
                .passwordHash("hash").rol(RolUsuario.USER).activo(true)
                .build();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());

        when(authenticationManager.authenticate(any())).thenReturn(
                new UsernamePasswordAuthenticationToken("juan@example.com", "password"));
        when(userRepository.findByEmail("juan@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getNombre()).isEqualTo("Juan");
    }
}
