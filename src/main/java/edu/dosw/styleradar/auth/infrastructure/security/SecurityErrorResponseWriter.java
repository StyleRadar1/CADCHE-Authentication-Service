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

    /** Mensajes exactos del catálogo 1.5 del contrato ms-auth ↔ Frontend; el detalle técnico va solo al log. */
    static final Map<CodigoError, String> MENSAJES_CONTRATO = Map.of(
            CodigoError.INVALID_TOKEN, "El token presentado es inválido o expirado",
            CodigoError.SERVICE_UNAVAILABLE,
            "El servicio requerido no está disponible temporalmente. Intente nuevamente más tarde.",
            CodigoError.RATE_LIMIT_EXCEEDED, "Demasiadas solicitudes en un período corto. Intente nuevamente más tarde.",
            CodigoError.INTERNAL_ERROR, "Ocurrió un error interno. Intente nuevamente más tarde.");

    /**
     * SR-RF-AUTH-01 FA-05: un token emitido antes de que la cuenta se inactivara o bloqueara responde con este
     * mensaje propio; es el único texto 401 que no se reemplaza por el del catálogo.
     */
    public static final String MENSAJE_SESION_REVOCADA = "El token ha sido revocado";

    /** SR-RF-112 FA-01: sin cabecera Authorization o sin el prefijo «Bearer». */
    public static final String MENSAJE_SIN_BEARER = "No autorizado: se requiere un token Bearer válido";
    /** SR-RF-112 FA-02: token vacío o que no tiene forma de JWT. */
    public static final String MENSAJE_TOKEN_MALFORMADO = "No autorizado: token vacío o malformado";
    /** SR-RF-112 FA-03: token con firma inválida, vencido o revocado. */
    public static final String MENSAJE_TOKEN_INVALIDO = "El token presentado es inválido o expirado";

    /** Mensajes 401 del análisis que se responden tal cual, sin reemplazarlos por el del catálogo. */
    private static final java.util.Set<String> MENSAJES_PROPIOS = java.util.Set.of(
            MENSAJE_SESION_REVOCADA, MENSAJE_SIN_BEARER, MENSAJE_TOKEN_MALFORMADO, MENSAJE_TOKEN_INVALIDO);

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

        String mensaje = MENSAJES_PROPIOS.contains(message) ? message : MENSAJES_CONTRATO.getOrDefault(codigo, message);
        ErrorResponseDTO body = ErrorResponseDTO.problema(status.value(), status.getReasonPhrase(), codigo, mensaje,
                        SecuritySanitizer.sanitizeForLog(path))
                .errors(Map.of())
                .retryAfterSeconds(esperarSegundos)
                .build();

        ObjectMapper mapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        response.getWriter().write(mapper.writeValueAsString(body));
    }

    static CodigoError codigoPorEstado(HttpStatus status) {
        return switch (status) {
            case UNAUTHORIZED -> CodigoError.INVALID_TOKEN;
            case FORBIDDEN -> CodigoError.ACCESS_DENIED;
            case TOO_MANY_REQUESTS -> CodigoError.RATE_LIMIT_EXCEEDED;
            case SERVICE_UNAVAILABLE -> CodigoError.SERVICE_UNAVAILABLE;
            case BAD_REQUEST -> CodigoError.INVALID_DATA;
            default -> CodigoError.INTERNAL_ERROR;
        };
    }
}
