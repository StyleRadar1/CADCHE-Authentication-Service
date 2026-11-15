package edu.dosw.styleradar.auth.application.dto.response;

import java.util.Set;

import edu.dosw.styleradar.auth.domain.model.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de autenticación con token JWT Bearer, roles RBAC asignados y estado de verificación del correo (SR-RF-02 / SR-RF-92)")
public record LoginResponseDTO(
        @Schema(description = "Token de acceso JWT firmado con HS256", example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsInJvbGVzIjpbIkNPTVBSQURPUiJdLCJpYXQiOjE3MzA2NTkyMDAsImV4cCI6MTczMDc0NTYwMH0.firma")
        String accessToken,

        @Schema(description = "Esquema de autenticación HTTP", example = "Bearer")
        String tokenType,

        @Schema(description = "Tiempo de vigencia del token en segundos (24 horas = 86400s)", example = "86400")
        long expiresIn,

        @Schema(description = "Identificador único del usuario autenticado", example = "12")
        Long usuarioId,

        @Schema(description = "Conjunto de roles RBAC activos del usuario (COMPRADOR, ADMIN_ALMACEN, REPRESENTANTE_FUNDACION, ADMIN_STYLERADAR)", example = "[\"COMPRADOR\"]")
        Set<Rol> roles,

        @Schema(description = "Indica si la sesión requiere completar la verificación del código OTP de 6 dígitos enviado al correo (SR-RF-92)", example = "false")
        Boolean requiereOtp,

        @Schema(description = "Tiempo de vigencia en segundos del código OTP almacenado en Redis bajo otp:{email} (5 minutos = 300s)", example = "300")
        Integer otpExpiraEnSegundos,

        @Schema(description = "Número máximo de intentos disponibles para validar el código OTP antes de bloqueo temporal", example = "3")
        Integer intentosOtpRestantes,

        @Schema(description = "Correo electrónico normalizado de la cuenta", example = "comprador@styleradar.com")
        String email,

        @Schema(description = "Indica si el correo de la cuenta ya fue verificado (SR-RF-02)", example = "true")
        Boolean correoVerificado,

        @Schema(description = "Indica si falta verificar el correo con código OTP (SR-RF-92)", example = "false")
        Boolean verificacionPendiente,

        @Schema(description = "Mensaje descriptivo del resultado del proceso de autenticación", example = "Inicio de sesión exitoso")
        String mensaje
) {
    public LoginResponseDTO(String accessToken, String tokenType, long expiresIn, Long usuarioId, Set<Rol> roles) {
        this(accessToken, tokenType, expiresIn, usuarioId, roles, Boolean.FALSE, 300, 3, null, Boolean.TRUE, Boolean.FALSE, "Inicio de sesión exitoso");
    }

    public LoginResponseDTO(
            String accessToken,
            String tokenType,
            long expiresIn,
            Long usuarioId,
            Set<Rol> roles,
            Boolean requiereOtp,
            Integer otpExpiraEnSegundos,
            Integer intentosOtpRestantes) {
        this(accessToken, tokenType, expiresIn, usuarioId, roles, requiereOtp, otpExpiraEnSegundos, intentosOtpRestantes, null,
                !Boolean.TRUE.equals(requiereOtp), Boolean.TRUE.equals(requiereOtp),
                Boolean.TRUE.equals(requiereOtp) ? "Código de verificación OTP enviado a su correo electrónico" : "Inicio de sesión exitoso");
    }
}
