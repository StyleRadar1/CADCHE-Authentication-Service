package edu.dosw.styleradar.auth.infrastructure.security;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Limitador de tasa distribuido sobre Redis (con respaldo local solo en perfil dev)
 * para prevenir ataques de fuerza bruta y abuso sobre endpoints sensibles de
 * autenticación (OWASP A06:2025 y A07:2025).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
@Slf4j
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_AUTH_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/otp/verificar",
            "/api/v1/auth/otp/reenviar",
            "/api/v1/auth/refresh",
            "/api/v1/auth/registro",
            "/api/v1/auth/oauth2/google",
            "/api/v1/auth/oauth2/google/code"
    );

    private static final Set<String> PROTECTED_GET_AUTH_PATHS = Set.of(
            "/api/v1/auth/oauth2/google/url",
            "/api/v1/auth/oauth2/google/callback"
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final boolean enabled;
    private final int maxRequests;
    private final long windowSeconds;
    private final boolean devLocalFallbackEnabled;

    private final Map<String, WindowCounter> localCounters = new ConcurrentHashMap<>();

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring e inyectadas por constructor.")
    @Autowired
    public RateLimitingFilter(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            Environment environment,
            @Value("${styleradar.security.rate-limit.enabled:true}") boolean enabled,
            @Value("${styleradar.security.rate-limit.max-requests:15}") int maxRequests,
            @Value("${styleradar.security.rate-limit.window-seconds:60}") long windowSeconds,
            @Value("${styleradar.security.redis-local-fallback-enabled:false}") boolean devLocalFallbackEnabled) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.enabled = enabled;
        this.maxRequests = maxRequests > 0 ? maxRequests : 15;
        this.windowSeconds = windowSeconds > 0 ? windowSeconds : 60L;
        this.devLocalFallbackEnabled = devLocalFallbackEnabled;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();
        if (!enabled || path == null || !isProtectedEndpoint(request.getMethod(), path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientIp = SecuritySanitizer.sanitizeForLog(request.getRemoteAddr());
        String key = "ratelimit:" + path + ":" + clientIp;

        long count;
        try {
            ValueOperations<String, String> ops = redisTemplate != null ? redisTemplate.opsForValue() : null;
            if (ops == null) {
                if (isDevFallbackActive()) {
                    count = incrementLocal(key);
                } else {
                    filterChain.doFilter(request, response);
                    return;
                }
            } else {
                Long incremented = ops.increment(key);
                count = incremented != null ? incremented : 1L;
                if (count == 1L) {
                    redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
                }
            }
        } catch (DataAccessException ex) {
            if (isDevFallbackActive()) {
                count = incrementLocal(key);
            } else {
                log.error("Fallo fail-closed en limitador de tasa por indisponibilidad de Redis en ruta {}: {}",
                        SecuritySanitizer.sanitizeForLog(path), SecuritySanitizer.sanitizeForLog(ex.getMessage()));
                SecurityErrorResponseWriter.write(
                        response, objectMapper, HttpStatus.SERVICE_UNAVAILABLE, path,
                        "El servicio de control de accesos no está disponible temporalmente.");
                return;
            }
        }

        if (count > maxRequests) {
            log.warn("Rate limit excedido ({}/{}) en ruta {} para cliente {}",
                    count, maxRequests, SecuritySanitizer.sanitizeForLog(path), clientIp);
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            SecurityErrorResponseWriter.write(
                    response, objectMapper, HttpStatus.TOO_MANY_REQUESTS, path,
                    "Demasiadas solicitudes en un período corto. Intente nuevamente más tarde.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private long incrementLocal(String key) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;
        WindowCounter updated = localCounters.compute(key, (k, current) -> {
            if (current == null || now > current.expiresAtMillis) {
                return new WindowCounter(1L, now + windowMillis);
            }
            return new WindowCounter(current.count + 1L, current.expiresAtMillis);
        });
        return updated.count;
    }

    private boolean isProtectedEndpoint(String method, String path) {
        if (HttpMethod.POST.matches(method)) {
            return PROTECTED_AUTH_PATHS.contains(path);
        }
        return HttpMethod.GET.matches(method) && PROTECTED_GET_AUTH_PATHS.contains(path);
    }

    private boolean isDevFallbackActive() {
        return devLocalFallbackEnabled && SecuritySanitizer.isDevProfileOnly(environment);
    }

    private record WindowCounter(long count, long expiresAtMillis) {
    }
}
