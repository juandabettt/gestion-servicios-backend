package com.tuapp.servicios.web.filter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class RateLimitingFilter implements Filter {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();
        String ip = getClientIp(httpReq);

        Bucket bucket = getBucket(path, ip, httpReq);
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit excedido para IP: {} en path: {}", ip, path);
            httpResp.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResp.setHeader("Retry-After", "60");
            httpResp.setContentType("application/json");
            httpResp.getWriter().write("""
                    {"status":429,"error":"Too Many Requests","message":"Demasiadas solicitudes. Intenta más tarde."}
                    """);
            return;
        }
        chain.doFilter(request, response);
    }

    private Bucket getBucket(String path, String ip, HttpServletRequest request) {
        String key = buildKey(path, ip, request);
        return buckets.computeIfAbsent(key, k -> createBucket(path));
    }

    private String buildKey(String path, String ip, HttpServletRequest request) {
        if (path.contains("/auth/login") || path.contains("/auth/register")) {
            return "ip:" + ip + ":path:" + path;
        }
        String userId = extractUserIdentifier(request);
        return "user:" + userId + ":path:" + path;
    }

    private Bucket createBucket(String path) {
        Bandwidth limit;
        if (path.contains("/auth/login")) {
            limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
        } else if (path.contains("/auth/register")) {
            limit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofHours(1)));
        } else if (path.contains("/invoices/upload")) {
            limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofHours(1)));
        } else if (path.contains("/ai-insights/analyze")) {
            limit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofDays(1)));
        } else {
            limit = Bandwidth.classic(100, Refill.intervally(100, Duration.ofMinutes(1)));
        }
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String extractUserIdentifier(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7, Math.min(auth.length(), 27));
        }
        return getClientIp(request);
    }
}
