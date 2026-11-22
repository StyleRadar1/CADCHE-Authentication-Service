package edu.dosw.styleradar.auth.application.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/**
 * Error en formato ProblemDetail (RFC 9457, {@code application/problem+json}) común a ms-auth, ms-usuarios y
 * ms-tiendas (CADCHE-121), con las extensiones del contrato ms-auth ↔ Frontend v1.2 (sección 1.4).
 * {@code error}, {@code message} y {@code path} se conservan como alias de {@code title}, {@code detail} e
 * {@code instance} para no romper al front mientras migra.
 */
@Value
@Builder
@Schema(description = "Error in ProblemDetail format (RFC 9457) with the StyleRadar extensions. Only detail/message and the per-field messages are in Spanish")
public class ErrorResponseDTO {

    /** Formato de fecha del contrato: AAAA-MM-DDThh:mm:ss, sin fracciones de segundo. */
    public static final String FORMATO_FECHA = "yyyy-MM-dd'T'HH:mm:ss";
    public static final String MEDIA_TYPE = "application/problem+json";
    public static final String TIPO_BASE = "https://api.styleradar.com/errors/";

    @Schema(description = "URI identifying the problem type: https://api.styleradar.com/errors/{code in lower case with hyphens}", example = "https://api.styleradar.com/errors/otp-attempts-exhausted")
    String type;

    @Schema(description = "HTTP status name (RFC 9457)", example = "Too Many Requests")
    String title;

    @Schema(description = "Problem explanation for the user (Spanish)", example = "Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.")
    String detail;

    @Schema(description = "Request path that produced the problem", example = "/api/v1/auth/otp/verify")
    String instance;

    @Schema(description = "Date and time of the error (compatibility alias)", example = "2026-11-13T10:15:30")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = FORMATO_FECHA)
    LocalDateTime timestamp;

    @Schema(description = "HTTP status code", example = "429")
    int status;

    @Schema(description = "Alias of title (compatibility)", example = "Too Many Requests")
    String error;

    @Schema(description = "Fixed error code; the front end picks the text to show with it", example = "OTP_ATTEMPTS_EXHAUSTED")
    CodigoError code;

    @Schema(description = "Alias of detail (compatibility)", example = "Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.")
    String message;

    @Schema(description = "Alias of instance (compatibility)", example = "/api/v1/auth/otp/verify")
    String path;

    @Schema(description = "Per-field validation messages (Spanish), only when an input field breaks its rule", example = "{\"email\":\"El formato del correo electrónico es inválido\"}")
    Map<String, String> errors;

    @Schema(description = "Attempts left for the OTP code (OTP_INCORRECT and OTP_ATTEMPTS_EXHAUSTED only)", example = "0", nullable = true)
    Integer attemptsRemaining;

    @Schema(description = "Seconds to wait before retrying; same as the Retry-After header (429 only)", example = "60", nullable = true)
    Long retryAfterSeconds;

    /**
     * Construye el problema con los campos RFC 9457 y sus alias de compatibilidad.
     */
    public static ErrorResponseDTOBuilder problema(int status, String titulo, CodigoError codigo, String detalle, String ruta) {
        return ErrorResponseDTO.builder()
                .type(TIPO_BASE + codigo.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'))
                .title(titulo)
                .status(status)
                .detail(detalle)
                .instance(ruta)
                .code(codigo)
                .timestamp(LocalDateTime.now())
                .error(titulo)
                .message(detalle)
                .path(ruta);
    }
}
