package edu.dosw.styleradar.auth.application.dto.response;

import java.util.Set;

import edu.dosw.styleradar.auth.domain.model.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de sesión del contrato ms-auth ↔ Frontend v1.2 (secciones 3.3, 4.1 y 6.1).
 */
@Schema(description = "Respuesta de autenticación: token JWT Bearer (o desafío OTP si el correo no está verificado), rol y estado de verificación del correo (SR-RF-02 / SR-RF-92)")
public record LoginResponseDTO(
        @Schema(description = "Token de acceso JWT firmado con HS256. Es null cuando el correo aún no está verificado y se exige el código OTP", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsInJvbGVzIjpbIkNPTVBSQURPUiJdfQ.firma", nullable = true)
        String accessToken,

        @Schema(description = "Esquema de autenticación HTTP", example = "Bearer")
        String tokenType,

        @Schema(description = "Vigencia del token en segundos, tomada de JWT_DURATION", example = "86400")
        long expiresIn,

        @Schema(description = "Identificador único del usuario", example = "12")
        Long usuarioId,

        @Schema(description = "Rol RBAC de la cuenta (COMPRADOR, ADMIN_ALMACEN, REPRESENTANTE_FUNDACION, ADMIN_STYLERADAR)", example = "[\"COMPRADOR\"]")
        Set<Rol> roles,

        @Schema(description = "Correo normalizado de la cuenta (null en /refresh)", example = "comprador@styleradar.com", nullable = true)
        String email,

        @Schema(description = "true si el correo ya fue verificado (entra sin código, RN-05)", example = "true")
        boolean correoVerificado,

        @Schema(description = "true si falta verificar el correo con el código OTP de 6 dígitos (SR-RF-92)", example = "false")
        boolean requiereOtp,

        @Schema(description = "Vigencia del código OTP en segundos (300). Null si no se exige código", example = "300", nullable = true)
        Long otpExpiraEnSegundos,

        @Schema(description = "Intentos disponibles para el código OTP (3). 0 si no se exige código", example = "0")
        int intentosOtpRestantes,

        @Schema(description = "Igual a requiereOtp. Se conserva por compatibilidad con el análisis de requerimientos", example = "false")
        boolean verificacionPendiente,

        @Schema(description = "Resumen del resultado del proceso de autenticación", example = "Inicio de sesión exitoso")
        String mensaje
) {
}
