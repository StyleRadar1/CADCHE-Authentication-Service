package edu.dosw.styleradar.auth.application.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "URL de autorización de Google OAuth2 con token state anti-CSRF generado por ms-auth")
public record GoogleAuthUrlResponseDTO(
        @Schema(
                description = "URL completa de Google Accounts para redirigir al usuario y obtener su consentimiento OIDC",
                example = "https://accounts.google.com/o/oauth2/v2/auth?client_id=xxx.apps.googleusercontent.com&redirect_uri=http%3A%2F%2Flocalhost%3A8100%2Fapi%2Fv1%2Fauth%2Foauth2%2Fgoogle%2Fcallback&response_type=code&scope=openid%20email%20profile&state=abc")
        String authorizationUrl,
        @Schema(
                description = "Token criptográfico aleatorio de un solo uso asociado al flujo OAuth2 (TTL configurado)",
                example = "estado-anti-csrf-ejemplo-12345")
        String state,
        @Schema(
                description = "Tiempo de vigencia en segundos del parámetro state en Redis",
                example = "300")
        long expiresInSeconds,
        @Schema(
                description = "URI de redirección efectiva incluida en la URL de autorización",
                example = "http://localhost:8100/api/v1/auth/oauth2/google/callback")
        String redirectUri) {
}

