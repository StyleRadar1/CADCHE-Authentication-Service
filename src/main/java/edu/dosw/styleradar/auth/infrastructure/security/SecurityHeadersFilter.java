package edu.dosw.styleradar.auth.infrastructure.security;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro HTTP que inyecta cabeceras defensivas contra Security Misconfiguration
 * (OWASP A02:2025), Clickjacking, MIME Sniffing y almacenamiento en caché de tokens.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter implements Filter {

    public static final String HEADER_X_CONTENT_TYPE_OPTIONS = "X-Content-Type-Options";
    public static final String HEADER_X_FRAME_OPTIONS = "X-Frame-Options";
    public static final String HEADER_X_XSS_PROTECTION = "X-XSS-Protection";
    public static final String HEADER_STRICT_TRANSPORT_SECURITY = "Strict-Transport-Security";
    public static final String HEADER_CONTENT_SECURITY_POLICY = "Content-Security-Policy";
    public static final String HEADER_REFERRER_POLICY = "Referrer-Policy";
    public static final String HEADER_PERMISSIONS_POLICY = "Permissions-Policy";
    public static final String HEADER_CACHE_CONTROL = "Cache-Control";
    public static final String HEADER_PRAGMA = "Pragma";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse httpResponse) {
            httpResponse.setHeader(HEADER_X_CONTENT_TYPE_OPTIONS, "nosniff");
            httpResponse.setHeader(HEADER_X_FRAME_OPTIONS, "DENY");
            httpResponse.setHeader(HEADER_X_XSS_PROTECTION, "1; mode=block");
            httpResponse.setHeader(HEADER_STRICT_TRANSPORT_SECURITY, "max-age=31536000; includeSubDomains; preload");
            httpResponse.setHeader(HEADER_CONTENT_SECURITY_POLICY,
                    "default-src 'self'; img-src 'self' data: https:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none';");
            httpResponse.setHeader(HEADER_REFERRER_POLICY, "strict-origin-when-cross-origin");
            httpResponse.setHeader(HEADER_PERMISSIONS_POLICY, "geolocation=(), microphone=(), camera=()");

            if (request instanceof HttpServletRequest httpRequest
                    && httpRequest.getRequestURI() != null
                    && httpRequest.getRequestURI().startsWith("/api/")) {
                httpResponse.setHeader(HEADER_CACHE_CONTROL, "no-store, no-cache, must-revalidate");
                httpResponse.setHeader(HEADER_PRAGMA, "no-cache");
            }
        }
        chain.doFilter(request, response);
    }
}

