package edu.dosw.styleradar.auth.application.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@Schema(description = "Estructura estándar de error retornada por el microservicio")
public class ErrorResponseDTO {

    @Schema(description = "Fecha y hora en que ocurrió el error", example = "2026-11-03T01:30:00")
    LocalDateTime timestamp;

    @Schema(description = "Código de estado HTTP", example = "400")
    int status;

    @Schema(description = "Nombre estándar del estado HTTP", example = "Bad Request")
    String error;

    @Schema(description = "Mensaje descriptivo del error para el cliente", example = "La solicitud contiene datos inválidos")
    String message;

    @Schema(description = "Ruta del endpoint invocado", example = "/api/v1/auth/login")
    String path;

    @Schema(description = "Detalle de errores de validación por campo cuando aplica", example = "{\"email\":\"El formato del correo electrónico es inválido\"}")
    Map<String, String> validationErrors;
}

