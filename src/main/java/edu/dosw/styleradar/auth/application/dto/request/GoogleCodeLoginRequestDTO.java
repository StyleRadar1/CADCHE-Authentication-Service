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
@Schema(description = "Google OAuth2 authorization code + anti-CSRF state login request")
public class GoogleCodeLoginRequestDTO {

    @NotBlank(message = "El código de autorización de Google es obligatorio")
    @Size(max = 2048, message = "El código de autorización excede la longitud máxima permitida")
    @Schema(
            description = "Temporary authorization code returned by Google after the consent screen",
            example = "4/0AdLIrYcEjemploAuthorizationCode",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "El parámetro state anti-CSRF es obligatorio")
    @Size(max = 256, message = "El parámetro state excede la longitud máxima permitida")
    @Schema(
            description = "State issued by GET /api/v1/auth/oauth2/google/url, single use (max 256 characters)",
            example = "anti-csrf-state-12345",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String state;

    @Size(max = 512, message = "La URI de redirección excede la longitud máxima permitida")
    @Schema(
            description = "Allowed redirect URI used to start the OAuth2 flow (optional, max 512 characters)",
            example = "http://localhost:8100/api/v1/auth/oauth2/google/callback",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
    private String redirectUri;
}

