package edu.dosw.styleradar.auth.application.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import edu.dosw.styleradar.auth.domain.model.CodigoError;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/**
 * Formato único de error del contrato ms-auth ↔ Frontend v1.2 (sección 1.4).
 */
@Value
@Builder
@Schema(description = "Estructura estándar de error retornada por el microservicio")
public class ErrorResponseDTO {

    @Schema(description = "Fecha y hora en que ocurrió el error", example = "2026-11-13T10:15:30")
    LocalDateTime timestamp;

    @Schema(description = "Código de estado HTTP", example = "429")
    int status;

    @Schema(description = "Nombre estándar del estado HTTP", example = "Too Many Requests")
    String error;

    @Schema(description = "Código fijo del catálogo de errores; el front elige el texto a mostrar con él (D3)", example = "OTP_INTENTOS_AGOTADOS")
    CodigoError codigo;

    @Schema(description = "Mensaje descriptivo del error (Swagger y logs)", example = "Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.")
    String message;

    @Schema(description = "Ruta del endpoint invocado", example = "/api/v1/auth/otp/verificar")
    String path;

    @Schema(description = "Detalle de errores de validación por campo cuando aplica", example = "{\"email\":\"El formato del correo electrónico es inválido\"}")
    Map<String, String> validationErrors;

    @Schema(description = "Intentos que quedan para el código OTP (solo en OTP_INCORRECTO y OTP_INTENTOS_AGOTADOS)", example = "0", nullable = true)
    Integer intentosRestantes;

    @Schema(description = "Segundos de espera antes de reintentar; igual a la cabecera Retry-After (solo en 429)", example = "60", nullable = true)
    Long esperarSegundos;
}
