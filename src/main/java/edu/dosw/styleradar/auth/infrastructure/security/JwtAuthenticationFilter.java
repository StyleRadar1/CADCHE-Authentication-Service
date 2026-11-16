package edu.dosw.styleradar.auth.infrastructure.security;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Filtro de autenticación stateless mediante JWT (OWASP A01:2025, A07:2025 y A09:2025).
 * Verifica firma HMAC-SHA256, vigencia temporal, lista negra por cierre de sesión
 * (blacklist:&lt;token&gt;) y revocación global por cambio de estado de cuenta.
 */
@Component
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final OtpService otpService;
    private final ObjectMapper objectMapper;
    private final Environment environment;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring e inyectadas por constructor.")
    @Autowired
    public JwtAuthenticationFilter(
            JwtService jwtService,
            StringRedisTemplate redisTemplate,
            OtpService otpService,
            ObjectMapper objectMapper,
            Environment environment) {
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.otpService = otpService;
        this.objectMapper = objectMapper;
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authorization.substring(7).trim();
        String safeUri = SecuritySanitizer.sanitizeForLog(request.getRequestURI());

        if (token.isEmpty()) {
            rejectRequest(response, HttpStatus.UNAUTHORIZED, safeUri, "Token inválido o vacío");
            return;
        }

        try {
            Claims claims = jwtService.validarToken(token);

            if (claims.getSubject() == null || claims.getIssuedAt() == null) {
                log.warn("Rechazo 401: token JWT sin subject o issuedAt en ruta {}", safeUri);
                rejectRequest(response, HttpStatus.UNAUTHORIZED, safeUri, "Token inválido o incompleto");
                return;
            }

            Long usuarioId = Long.valueOf(claims.getSubject());
            Date issuedAt = claims.getIssuedAt();
            long fechaEmision = issuedAt.getTime();

            if (otpService != null && otpService.esTokenInvalido(token)) {
                log.warn("Rechazo 401: token JWT en blacklist para usuario ID {} en ruta {}", usuarioId, safeUri);
                rejectRequest(response, HttpStatus.UNAUTHORIZED, safeUri, "El token ha sido revocado");
                return;
            }

            if (isUserRevoked(usuarioId, fechaEmision, safeUri)) {
                log.warn("Rechazo 401: sesión revocada globalmente para usuario ID {} en ruta {}", usuarioId, safeUri);
                rejectRequest(response, HttpStatus.UNAUTHORIZED, safeUri, "El token ha sido revocado");
                return;
            }

            List<SimpleGrantedAuthority> authorities = extractValidAuthorities(claims);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            claims.getSubject(),
                            null,
                            authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (DataAccessException exception) {
            log.error("Fallo fail-closed validando revocación en Redis en ruta {}: {}",
                    safeUri, SecuritySanitizer.sanitizeForLog(exception.getMessage()));
            rejectRequest(response, HttpStatus.SERVICE_UNAVAILABLE, safeUri,
                    "El servicio de verificación de sesiones no está disponible temporalmente.");
            return;
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Rechazo 401: token JWT inválido o expirado en ruta {}", safeUri);
            rejectRequest(response, HttpStatus.UNAUTHORIZED, safeUri, "Token inválido o expirado");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isUserRevoked(Long usuarioId, long fechaEmision, String safeUri) {
        try {
            ValueOperations<String, String> ops = redisTemplate != null ? redisTemplate.opsForValue() : null;
            if (ops == null) {
                return false;
            }
            String fechaRevocacion = ops.get("usuario:revocado:" + usuarioId);
            if (fechaRevocacion == null) {
                return false;
            }
            long revocacionMillis = Long.parseLong(fechaRevocacion);
            return fechaEmision <= revocacionMillis;
        } catch (NumberFormatException ex) {
            log.error("Marca temporal de revocación corrupta en Redis para usuario ID {} en ruta {}",
                    usuarioId, safeUri);
            return true;
        } catch (DataAccessException ex) {
            if (SecuritySanitizer.isDevProfileOnly(environment)) {
                log.debug("Redis no disponible en perfil dev al consultar revocación para usuario ID {}", usuarioId);
                return false;
            }
            throw ex;
        }
    }

    private List<SimpleGrantedAuthority> extractValidAuthorities(Claims claims) {
        List<?> rawRoles = claims.get("roles", List.class);
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        if (rawRoles == null) {
            return authorities;
        }

        for (Object rawRole : rawRoles) {
            try {
                Rol rol = Rol.valueOf(String.valueOf(rawRole));
                authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.name()));
            } catch (IllegalArgumentException ex) {
                log.warn("Claim de rol desconocido ignorado durante validación JWT");
            }
        }
        return authorities;
    }

    private void rejectRequest(
            HttpServletResponse response,
            HttpStatus status,
            String path,
            String message) throws IOException {
        SecurityContextHolder.clearContext();
        SecurityErrorResponseWriter.write(response, objectMapper, status, path, message);
    }
}
