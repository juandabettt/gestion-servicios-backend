package com.tuapp.servicios.application.service;

import com.tuapp.servicios.application.dto.request.LoginRequest;
import com.tuapp.servicios.application.dto.request.RegisterRequest;
import com.tuapp.servicios.application.dto.response.AuthResponse;
import com.tuapp.servicios.application.exception.BusinessException;
import com.tuapp.servicios.application.exception.DuplicateResourceException;
import com.tuapp.servicios.application.exception.ResourceNotFoundException;
import com.tuapp.servicios.domain.enums.EstadoRefreshToken;
import com.tuapp.servicios.domain.enums.ResultadoAudit;
import com.tuapp.servicios.domain.enums.RolUsuario;
import com.tuapp.servicios.domain.model.RefreshToken;
import com.tuapp.servicios.domain.model.User;
import com.tuapp.servicios.domain.model.UserPreferences;
import com.tuapp.servicios.domain.repository.RefreshTokenRepository;
import com.tuapp.servicios.domain.repository.UserPreferencesRepository;
import com.tuapp.servicios.domain.repository.UserRepository;
import com.tuapp.servicios.infrastructure.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final AuditService auditService;
    private final RedisTemplate<String, String> stringRedisTemplate;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("El email ya está registrado: " + request.getEmail());
        }
        User user = User.builder()
                .nombre(request.getNombre())
                .email(request.getEmail().toLowerCase().trim())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .rol(RolUsuario.USER)
                .activo(true)
                .telefono(request.getTelefono())
                .build();
        userRepository.save(user);
        preferencesRepository.save(UserPreferences.builder().user(user).build());
        auditService.log(user.getId(), "USER_REGISTERED", "User", user.getId(),
                ResultadoAudit.EXITO, "Registro exitoso");
        log.info("Usuario registrado exitosamente");
        return generateTokenPair(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        user.setUltimoLogin(LocalDateTime.now());
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(user.getId());
        auditService.log(user.getId(), "USER_LOGIN", "User", user.getId(),
                ResultadoAudit.EXITO, "Login exitoso");
        return generateTokenPair(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenStr) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenAndEstado(refreshTokenStr, EstadoRefreshToken.ACTIVO)
                .orElseThrow(() -> new BusinessException("Refresh token inválido o expirado", HttpStatus.UNAUTHORIZED));
        if (refreshToken.isExpired()) {
            refreshToken.setEstado(EstadoRefreshToken.EXPIRADO);
            refreshTokenRepository.save(refreshToken);
            throw new BusinessException("Refresh token expirado", HttpStatus.UNAUTHORIZED);
        }
        refreshToken.setEstado(EstadoRefreshToken.REVOCADO);
        refreshTokenRepository.save(refreshToken);
        return generateTokenPair(refreshToken.getUser());
    }

    @Transactional
    public void logout(String accessToken, UUID userId) {
        refreshTokenRepository.revokeAllByUserId(userId);
        if (accessToken != null) {
            try {
                String jti = jwtTokenProvider.extractJti(accessToken);
                Date expiry = jwtTokenProvider.extractExpiration(accessToken);
                long ttl = expiry.getTime() - System.currentTimeMillis();
                if (ttl > 0) {
                    stringRedisTemplate.opsForValue().set("revoked:jti:" + jti, "1", ttl, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.warn("Error al revocar access token en Redis");
            }
        }
        auditService.log(userId, "USER_LOGOUT", "User", userId, ResultadoAudit.EXITO, "Logout exitoso");
    }

    private AuthResponse generateTokenPair(User user) {
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.getEmail(), user.getId().toString(), user.getRol().name());
        String refreshTokenStr = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshTokenStr)
                .estado(EstadoRefreshToken.ACTIVO)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpirationMs / 1000))
                .createdAt(LocalDateTime.now())
                .build();
        refreshTokenRepository.save(refreshToken);
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .userId(user.getId())
                .nombre(user.getNombre())
                .rol(user.getRol().name())
                .build();
    }
}
