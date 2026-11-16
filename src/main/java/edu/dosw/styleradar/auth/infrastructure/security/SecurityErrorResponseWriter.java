package edu.dosw.styleradar.auth.infrastructure.security;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.dto.response.ErrorResponseDTO;
import edu.dosw.styleradar.auth.domain.model.CodigoError;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Escritor uniforme de respuestas ProblemDetail {@link ErrorResponseDTO} para filtros
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
        write(response, objectMapper, status, codigoPorEstado(status), path, message, null);
    }

    public static void write(
            HttpServletResponse response,
            ObjectMapper objectMapper,
            HttpStatus status,
            CodigoError codigo,
            String path,
            String message,
            Long esperarSegundos) throws IOException {
        response.setStatus(status.value());
        response.setContentType(ErrorResponseDTO.MEDIA_TYPE);
        response.setCharacterEncoding("UTF-8");
        if (esperarSegundos != null) {
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(esperarSegundos));
        }

        ErrorResponseDTO body = ErrorResponseDTO.problema(status.value(), status.getReasonPhrase(), codigo, message,
                        SecuritySanitizer.sanitizeForLog(path))
                .validationErrors(Map.of())
                .esperarSegundos(esperarSegundos)
                .build();

        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        response.getWriter().write(mapper.writeValueAsString(body));
    }

    static CodigoError codigoPorEstado(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> CodigoError.TOKEN_INVALIDO;
            case FORBIDDEN -> CodigoError.ACCESO_DENEGADO;
            case TOO_MANY_REQUESTS -> CodigoError.LIMITE_SOLICITUDES;
            case SERVICE_UNAVAILABLE -> CodigoError.SERVICIO_NO_DISPONIBLE;
            case BAD_REQUEST -> CodigoError.DATOS_INVALIDOS;
            default -> CodigoError.ERROR_INTERNO;
        };
    }
}
