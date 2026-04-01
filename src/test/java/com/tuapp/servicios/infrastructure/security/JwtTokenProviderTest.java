package com.tuapp.servicios.infrastructure.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // 256-bit key en base64
    private static final String TEST_SECRET = Base64.getEncoder().encodeToString(
            "testSecretKeyForUnitTests2025AES256BitsOK!".getBytes()
    );

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecretBase64", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "accessExpirationMs", 900_000L); // 15 min
    }

    @Test
    void generateAccessToken_createsValidToken() {
        String email = "test@example.com";
        String userId = UUID.randomUUID().toString();

        String token = jwtTokenProvider.generateAccessToken(email, userId, "USER");

        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void extractEmail_returnsCorrectEmail() {
        String email = "juan@example.com";
        String token = jwtTokenProvider.generateAccessToken(email, UUID.randomUUID().toString(), "USER");

        assertThat(jwtTokenProvider.extractEmail(token)).isEqualTo(email);
    }

    @Test
    void extractUserId_returnsCorrectUserId() {
        String userId = UUID.randomUUID().toString();
        String token = jwtTokenProvider.generateAccessToken("test@example.com", userId, "USER");

        assertThat(jwtTokenProvider.extractUserId(token)).isEqualTo(userId);
    }

    @Test
    void extractJti_returnsNonNullJti() {
        String token = jwtTokenProvider.generateAccessToken("test@test.com", UUID.randomUUID().toString(), "USER");

        assertThat(jwtTokenProvider.extractJti(token)).isNotNull();
    }

    @Test
    void validateToken_withValidToken_returnsTrue() {
        String token = jwtTokenProvider.generateAccessToken("valid@test.com", UUID.randomUUID().toString(), "USER");

        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_withMalformedToken_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken("not.a.valid.jwt")).isFalse();
    }

    @Test
    void validateToken_withExpiredToken_returnsFalse() {
        // Crear token con expiración en el pasado
        ReflectionTestUtils.setField(jwtTokenProvider, "accessExpirationMs", -1000L);
        String expiredToken = jwtTokenProvider.generateAccessToken("expired@test.com", UUID.randomUUID().toString(), "USER");

        assertThat(jwtTokenProvider.validateToken(expiredToken)).isFalse();
    }

    @Test
    void validateToken_withNullToken_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken(null)).isFalse();
    }

    @Test
    void validateToken_withEmptyToken_returnsFalse() {
        assertThat(jwtTokenProvider.validateToken("")).isFalse();
    }

    @Test
    void extractExpiration_returnsDateInFuture() {
        String token = jwtTokenProvider.generateAccessToken("test@test.com", UUID.randomUUID().toString(), "ADMIN");

        Date expiry = jwtTokenProvider.extractExpiration(token);
        assertThat(expiry).isAfter(new Date());
    }
}
