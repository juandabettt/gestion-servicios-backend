package com.tuapp.servicios.infrastructure.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecretBase64;

    @Value("${jwt.access-expiration-ms}")
    private long accessExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(jwtSecretBase64);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String email, String userId, String rol) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessExpirationMs);
        String jti = UUID.randomUUID().toString();

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("rol", rol)
                .id(jti)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractUserId(String token) {
        return parseClaims(token).get("userId", String.class);
    }

    public String extractJti(String token) {
        return parseClaims(token).getId();
    }

    public Date extractExpiration(String token) {
        return parseClaims(token).getExpiration();
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token JWT expirado");
        } catch (UnsupportedJwtException e) {
            log.warn("Token JWT no soportado");
        } catch (MalformedJwtException e) {
            log.warn("Token JWT malformado");
        } catch (SecurityException e) {
            log.warn("Firma JWT inválida");
        } catch (IllegalArgumentException e) {
            log.warn("Token JWT vacío o nulo");
        }
        return false;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
