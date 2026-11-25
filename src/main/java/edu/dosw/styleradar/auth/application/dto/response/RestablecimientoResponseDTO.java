package edu.dosw.styleradar.auth.application.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "PasswordResetResponse", description = "Result of a password reset step (Spanish text for the end user)")
public record RestablecimientoResponseDTO(
        @Schema(description = "Message for the end user", example = "Si el correo está registrado, te enviamos un código para restablecer tu contraseña.")
        String message,

        @Schema(description = "Code lifetime in seconds (only when requesting the code)", example = "300")
        Long expiresInSeconds
) {
}
