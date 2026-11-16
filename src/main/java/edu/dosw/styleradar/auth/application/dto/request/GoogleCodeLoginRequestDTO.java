package edu.dosw.styleradar.auth.application.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud de autenticación mediante Google OAuth2 Authorization Code + state anti-CSRF")
public class GoogleCodeLoginRequestDTO {

    @NotBlank(message = "El código de autorización de Google es obligatorio")
    @Size(max = 2048, message = "El código de autorización excede la longitud máxima permitida")
    @Schema(
            description = "Código temporal (authorization code) devuelto por Google tras la pantalla de consentimiento",
            example = "4/0AdLIrYcEjemploAuthorizationCode",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "El parámetro state anti-CSRF es obligatorio")
    @Size(max = 256, message = "El parámetro state excede la longitud máxima permitida")
    @Schema(
            description = "Token state generado por GET /api/v1/auth/oauth2/google/url y validado de un solo uso",
            example = "estado-anti-csrf-ejemplo-12345",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String state;

    @Size(max = 512, message = "La URI de redirección excede la longitud máxima permitida")
    @Schema(
            description = "URI de redirección autorizada utilizada al iniciar el flujo OAuth2 (opcional; por defecto usa la configurada en ms-auth)",
            example = "http://localhost:8100/api/v1/auth/oauth2/google/callback",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String redirectUri;
}

