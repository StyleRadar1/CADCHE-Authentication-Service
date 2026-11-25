package edu.dosw.styleradar.auth.infrastructure.security;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Propaga X-Correlation-Id / X-Request-Id hacia el MDC de logs y las cabeceras de respuesta (BUG-031).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE - 1)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String MDC_KEY = "correlationId";
    private static final int MAX_LEN = 128;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String correlationId = resolverCorrelationId(request);
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_CORRELATION_ID, correlationId);
        response.setHeader(HEADER_REQUEST_ID, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolverCorrelationId(HttpServletRequest request) {
        String candidate = request.getHeader(HEADER_CORRELATION_ID);
        if (candidate == null || candidate.isBlank()) {
            candidate = request.getHeader(HEADER_REQUEST_ID);
        }
        if (candidate == null || candidate.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String sanitized = SecuritySanitizer.sanitizeForLog(candidate.trim());
        if (sanitized.isBlank() || "[null]".equals(sanitized)) {
            return UUID.randomUUID().toString();
        }
        return sanitized.length() > MAX_LEN ? sanitized.substring(0, MAX_LEN) : sanitized;
    }
}
