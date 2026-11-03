package edu.dosw.styleradar.auth.application.dto.response;

import java.util.Set;

import edu.dosw.styleradar.auth.domain.model.Rol;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Respuesta de autenticación con token JWT Bearer, roles RBAC asignados y estado del desafío OTP de segundo factor (SR-RF-02 / SR-RF-92)")
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

        @Schema(description = "Indica si la sesión requiere completar la verificación del código OTP de 6 dígitos enviado al correo (SR-RF-92)", example = "true")
        Boolean requiereOtp,

        @Schema(description = "Tiempo de vigencia en segundos del código OTP almacenado en Redis bajo otp:{email} (5 minutos = 300s)", example = "300")
        Integer otpExpiraEnSegundos,

        @Schema(description = "Número máximo de intentos disponibles para validar el código OTP antes de bloqueo temporal", example = "3")
        Integer intentosOtpRestantes
) {
    public LoginResponseDTO(String accessToken, String tokenType, long expiresIn, Long usuarioId, Set<Rol> roles) {
        this(accessToken, tokenType, expiresIn, usuarioId, roles, Boolean.FALSE, 300, 3);
    }
}
