package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import edu.dosw.styleradar.auth.application.service.GoogleIdentityClaims;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.port.out.GoogleOAuthPort;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public final class GoogleOAuthAdapter implements GoogleOAuthPort {

    private static final Set<String> GOOGLE_ISSUERS = Set.of(
            "https://accounts.google.com",
            "accounts.google.com");

    private final RestClient restClient;
    private final Set<String> allowedClientIds;
    private final String primaryClientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String jwkSetUri;
    private JwtDecoder jwtDecoder;

    public GoogleOAuthAdapter(
            RestClient.Builder restClientBuilder,
            String clientId,
            String clientSecret,
            String tokenUri,
            String jwkSetUri,
            Environment environment) {
        this(restClientBuilder, clientId, clientSecret, tokenUri, jwkSetUri, environment, null);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring o inyectadas en pruebas unitarias.")
    @Autowired
    public GoogleOAuthAdapter(
            RestClient.Builder restClientBuilder,
            @Value("${styleradar.security.oauth2.google.client-id:}") String clientId,
            @Value("${styleradar.security.oauth2.google.client-secret:}") String clientSecret,
            @Value("${styleradar.security.oauth2.google.token-uri:https://oauth2.googleapis.com/token}") String tokenUri,
            @Value("${styleradar.security.oauth2.google.jwk-set-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwkSetUri,
            Environment environment,
            @Autowired(required = false) JwtDecoder customJwtDecoder) {

        this.allowedClientIds = parseAllowedClientIds(clientId);
        this.primaryClientId = this.allowedClientIds.stream().findFirst().orElse("");
        this.clientSecret = clientSecret != null ? clientSecret.trim() : "";
        this.tokenUri = SecuritySanitizer.validateExternalServiceUrl(
                tokenUri, environment, "GOOGLE_TOKEN_URI");
        this.jwkSetUri = SecuritySanitizer.validateExternalServiceUrl(
                jwkSetUri, environment, "GOOGLE_JWK_SET_URI");
        this.restClient = restClientBuilder.build();

        if (customJwtDecoder instanceof NimbusJwtDecoder nimbus && !this.allowedClientIds.isEmpty()) {
            nimbus.setJwtValidator(construirValidadorGoogle(this.allowedClientIds));
        }
        this.jwtDecoder = customJwtDecoder;
    }

    @Override
    public GoogleIdentityClaims verificarIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new CredencialesIncorrectasException("El ID Token de Google es obligatorio");
        }
        if (allowedClientIds.isEmpty()) {
            throw new ServicioExternoNoDisponibleException(
                    "La autenticación con Google OAuth2 no está configurada en este entorno (GOOGLE_CLIENT_ID faltante)");
        }

        Jwt jwt;
        try {
            jwt = getOrCreateJwtDecoder().decode(idToken.trim());
        } catch (JwtException ex) {
            if (contieneErrorDeConectividad(ex)) {
                log.error("Fallo de conectividad verificando JWKS de Google en {}: {}",
                        SecuritySanitizer.sanitizeForLog(jwkSetUri),
                        SecuritySanitizer.sanitizeForLog(ex.getMessage()));
                throw new ServicioExternoNoDisponibleException(
                        "No fue posible validar las llaves públicas de Google Identity", ex);
            }
            log.warn("ID Token de Google rechazado durante verificación criptográfica/claims: {}",
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new CredencialesIncorrectasException(
                    "El ID Token de Google es inválido, expiró o no pertenece a StyleRadar");
        }

        String subject = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (subject == null || subject.isBlank() || email == null || email.isBlank()) {
            throw new CredencialesIncorrectasException(
                    "El ID Token de Google no contiene identificador (sub) o correo electrónico");
        }

        boolean emailVerified = esEmailVerificado(jwt.getClaims().get("email_verified"));
        if (!emailVerified) {
            log.warn("ID Token de Google rechazado por correo electrónico no verificado: {}",
                    SecuritySanitizer.maskEmail(email));
            throw new CredencialesIncorrectasException(
                    "La cuenta de Google no tiene el correo electrónico verificado");
        }

        String name = jwt.getClaimAsString("name");
        return new GoogleIdentityClaims(
                subject.trim(),
                SecuritySanitizer.normalizeEmail(email),
                true,
                name != null ? name.trim() : "");
    }

    @Override
    public String intercambiarCodigoPorIdToken(String code, String redirectUri) {
        if (code == null || code.isBlank() || redirectUri == null || redirectUri.isBlank()) {
            throw new IllegalArgumentException(
                    "El código de autorización y redirectUri de Google son obligatorios");
        }
        if (primaryClientId.isBlank() || clientSecret.isBlank()) {
            throw new ServicioExternoNoDisponibleException(
                    "El cliente confidencial de Google OAuth2 no está configurado en este entorno (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET)");
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code.trim());
        formData.add("client_id", primaryClientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri.trim());
        formData.add("grant_type", "authorization_code");

        try {
            Map<String, Object> tokenResponse = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });

            Object rawIdToken = tokenResponse != null ? tokenResponse.get("id_token") : null;
            if (!(rawIdToken instanceof String idToken) || idToken.isBlank()) {
                throw new CredencialesIncorrectasException(
                        "Google OAuth2 no retornó un id_token válido en el intercambio del código");
            }
            return idToken.trim();
        } catch (HttpClientErrorException ex) {
            log.warn("Google OAuth2 rechazó el authorization code con estado HTTP {}",
                    ex.getStatusCode().value());
            throw new CredencialesIncorrectasException(
                    "El código de autorización de Google es inválido, expiró o ya fue utilizado");
        } catch (RestClientResponseException ex) {
            log.error("Error HTTP {} comunicándose con Google Token Endpoint: {}",
                    ex.getStatusCode().value(),
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de tokens de Google OAuth2 no está disponible temporalmente", ex);
        } catch (ResourceAccessException ex) {
            log.error("Error de red comunicándose con Google Token Endpoint {}: {}",
                    SecuritySanitizer.sanitizeForLog(tokenUri),
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "No fue posible conectar con Google OAuth2 para intercambiar el código", ex);
        }
    }

    public OAuth2TokenValidator<Jwt> construirValidadorGoogle(Set<String> validClientIds) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                validarIssuerGoogle(),
                validarAudienceGoogle(validClientIds));
    }

    private synchronized JwtDecoder getOrCreateJwtDecoder() {
        if (this.jwtDecoder == null) {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
            decoder.setJwtValidator(construirValidadorGoogle(allowedClientIds));
            this.jwtDecoder = decoder;
        }
        return this.jwtDecoder;
    }

    private static OAuth2TokenValidator<Jwt> validarIssuerGoogle() {
        return token -> {
            String issuer = token.getClaimAsString("iss");
            if (issuer != null && GOOGLE_ISSUERS.contains(issuer.trim())) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_issuer",
                    "El emisor (iss) del ID Token no corresponde a Google Identity",
                    null));
        };
    }

    private static OAuth2TokenValidator<Jwt> validarAudienceGoogle(Set<String> validClientIds) {
        return token -> {
            List<String> audiences = token.getAudience();
            if (audiences != null && audiences.stream().anyMatch(validClientIds::contains)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_audience",
                    "La audiencia (aud) del ID Token no corresponde al client_id de StyleRadar",
                    null));
        };
    }

    private static boolean esEmailVerificado(Object claimValue) {
        if (claimValue instanceof Boolean boolVal) {
            return boolVal;
        }
        if (claimValue instanceof String strVal) {
            return "true".equalsIgnoreCase(strVal.trim());
        }
        return false;
    }

    private static boolean contieneErrorDeConectividad(Throwable error) {
        Throwable cursor = error;
        while (cursor != null) {
            if (cursor instanceof ResourceAccessException || cursor instanceof IOException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static Set<String> parseAllowedClientIds(String rawClientIds) {
        if (rawClientIds == null || rawClientIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rawClientIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
