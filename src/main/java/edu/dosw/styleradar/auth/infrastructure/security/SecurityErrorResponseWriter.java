package edu.dosw.styleradar.auth.infrastructure.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.dto.response.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Escritor uniforme de respuestas JSON {@link ErrorResponseDTO} para filtros
 * y puntos de entrada de Spring Security (OWASP A09:2025 y A10:2025).
 */
public final class SecurityErrorResponseWriter {

    private SecurityErrorResponseWriter() {
    }

    public static void write(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            String path,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponseDTO body = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(SecuritySanitizer.sanitizeForLog(path))
                .validationErrors(Map.of())
                .build();

        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        response.getWriter().write(mapper.writeValueAsString(body));
    }
}

