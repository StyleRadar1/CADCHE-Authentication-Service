package edu.dosw.styleradar.auth.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "OtpMessageResponse", description = "Confirmation of the new OTP code sent by email (FA-07)")
public record OtpMensajeResponseDTO(
        @Schema(description = "Email the OTP code was sent to", example = "buyer@styleradar.com")
        String email,

        @Schema(description = "Result summary (Spanish text for the end user)", example = "Se envió un código de verificación de 6 dígitos a su correo registrado")
        String message,

        @Schema(description = "OTP code lifetime in seconds", example = "300")
        long expiresInSeconds,

        @Schema(description = "Attempts available for the new code", example = "3")
        int attemptsRemaining,

        @Schema(description = "How many more codes can still be requested in this verification (2 to 0)", example = "2")
        int resendsRemaining
) {
}
