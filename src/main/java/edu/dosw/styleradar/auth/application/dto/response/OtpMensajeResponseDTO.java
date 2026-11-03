package edu.dosw.styleradar.auth.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Estado del desafío de verificación OTP enviado al correo electrónico (SR-RF-92)")
public record OtpMensajeResponseDTO(
        @Schema(description = "Correo electrónico al que se envió el código OTP", example = "comprador@styleradar.com")
        String email,

        @Schema(description = "Mensaje descriptivo del estado del desafío OTP", example = "Se envió un código de verificación de 6 dígitos a su correo registrado")
        String mensaje,

        @Schema(description = "Tiempo de expiración del código OTP en segundos (TTL en Redis)", example = "300")
        long expiraEnSegundos,

        @Schema(description = "Número máximo de intentos restantes para validar el código actual", example = "3")
        int intentosRestantes
) {
}

