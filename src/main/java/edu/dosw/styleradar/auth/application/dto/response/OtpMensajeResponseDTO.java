package edu.dosw.styleradar.auth.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Confirmación del nuevo código OTP enviado al correo (SR-RF-92, contrato v1.2 sección 4.2)")
public record OtpMensajeResponseDTO(
        @Schema(description = "Correo electrónico al que se envió el código OTP", example = "comprador@styleradar.com")
        String email,

        @Schema(description = "Mensaje descriptivo del estado del desafío OTP", example = "Se envió un código de verificación de 6 dígitos a su correo registrado")
        String mensaje,

        @Schema(description = "Vigencia del código OTP en segundos", example = "300")
        long expiraEnSegundos,

        @Schema(description = "Intentos disponibles para el nuevo código", example = "3")
        int intentosRestantes,

        @Schema(description = "Cuántas veces más puede pedir un código nuevo en esta verificación (2 a 0)", example = "2")
        int reenviosRestantes
) {
}
