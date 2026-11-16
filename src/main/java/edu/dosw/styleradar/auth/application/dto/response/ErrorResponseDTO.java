package edu.dosw.styleradar.auth.application.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

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
@Schema(description = "Error en formato ProblemDetail (RFC 9457) con las extensiones del contrato StyleRadar")
public class ErrorResponseDTO {

    public static final String MEDIA_TYPE = "application/problem+json";
    public static final String TIPO_BASE = "https://api.styleradar.com/errores/";

    @Schema(description = "URI que identifica el tipo de problema (RFC 9457)", example = "https://api.styleradar.com/errores/otp-intentos-agotados")
    String type;

    @Schema(description = "Resumen del problema: nombre del estado HTTP (RFC 9457)", example = "Too Many Requests")
    String title;

    @Schema(description = "Explicación del problema para esta ocurrencia (RFC 9457)", example = "Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.")
    String detail;

    @Schema(description = "Ruta de la solicitud que produjo el problema (RFC 9457)", example = "/api/v1/auth/otp/verificar")
    String instance;

    @Schema(description = "Fecha y hora en que ocurrió el error", example = "2026-11-13T10:15:30")
    LocalDateTime timestamp;

    @Schema(description = "Código de estado HTTP", example = "429")
    int status;

    @Schema(description = "Alias de title (compatibilidad)", example = "Too Many Requests")
    String error;

    @Schema(description = "Código fijo del catálogo de errores; el front elige el texto a mostrar con él (D3)", example = "OTP_INTENTOS_AGOTADOS")
    CodigoError codigo;

    @Schema(description = "Alias de detail (compatibilidad)", example = "Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.")
    String message;

    @Schema(description = "Alias de instance (compatibilidad)", example = "/api/v1/auth/otp/verificar")
    String path;

    @Schema(description = "Detalle de errores de validación por campo cuando aplica", example = "{\"email\":\"El formato del correo electrónico es inválido\"}")
    Map<String, String> validationErrors;

    @Schema(description = "Intentos que quedan para el código OTP (solo en OTP_INCORRECTO y OTP_INTENTOS_AGOTADOS)", example = "0", nullable = true)
    Integer intentosRestantes;

    @Schema(description = "Segundos de espera antes de reintentar; igual a la cabecera Retry-After (solo en 429)", example = "60", nullable = true)
    Long esperarSegundos;

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
                .codigo(codigo)
                .timestamp(LocalDateTime.now())
                .error(titulo)
                .message(detalle)
                .path(ruta);
    }
}
