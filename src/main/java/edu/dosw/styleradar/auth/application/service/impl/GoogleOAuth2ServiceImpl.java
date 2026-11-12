package edu.dosw.styleradar.auth.application.service.impl;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import edu.dosw.styleradar.auth.application.dto.response.GoogleAuthUrlResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.application.service.GoogleIdentityClaims;
import edu.dosw.styleradar.auth.application.service.GoogleOAuth2Service;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.port.out.GoogleOAuthPort;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class GoogleOAuth2ServiceImpl implements GoogleOAuth2Service {

    private static final String KEY_OAUTH_STATE = "oauth2:state:";
    private static final String PROVEEDOR_GOOGLE = "GOOGLE";
    private static final int STATE_BYTES = 32;

    private record LocalStateEntry(String redirectUri, long expiresAtMillis) {
    }

    private final GoogleOAuthPort googleOAuthPort;
    private final UsuarioClient usuarioClient;
    private final JwtService jwtService;
    private final StringRedisTemplate redisTemplate;
    private final Environment environment;
    private final String primaryClientId;
    private final String defaultRedirectUri;
    private final Set<String> allowedRedirectUris;
    private final String authorizationUri;
    private final long stateTtlSeconds;
    private final long jwtExpiresInSeconds;
    private final boolean localFallbackEnabled;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, LocalStateEntry> localStateStore = new ConcurrentHashMap<>();

    public GoogleOAuth2ServiceImpl(
            GoogleOAuthPort googleOAuthPort,
            UsuarioClient usuarioClient,
            JwtService jwtService,
            StringRedisTemplate redisTemplate,
            String clientId,
            String defaultRedirectUri,
            String allowedRedirectUris,
            long stateTtlSeconds,
            boolean localFallbackEnabled) {
        this(
                googleOAuthPort,
                usuarioClient,
                jwtService,
                redisTemplate,
                null,
                clientId,
                defaultRedirectUri,
                allowedRedirectUris,
                "https://accounts.google.com/o/oauth2/v2/auth",
                stateTtlSeconds,
                "PT24H",
                localFallbackEnabled);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring e inyectadas por constructor.")
    @Autowired
    public GoogleOAuth2ServiceImpl(
            GoogleOAuthPort googleOAuthPort,
            UsuarioClient usuarioClient,
            JwtService jwtService,
            StringRedisTemplate redisTemplate,
            Environment environment,
            @Value("${styleradar.security.oauth2.google.client-id:}") String clientId,
            @Value("${styleradar.security.oauth2.google.redirect-uri:http://localhost:8100/api/v1/auth/oauth2/google/callback}") String defaultRedirectUri,
            @Value("${styleradar.security.oauth2.google.allowed-redirect-uris:http://localhost:8100/api/v1/auth/oauth2/google/callback}") String allowedRedirectUris,
            @Value("${styleradar.security.oauth2.google.authorization-uri:https://accounts.google.com/o/oauth2/v2/auth}") String authorizationUri,
            @Value("${styleradar.security.oauth2.google.state-ttl-seconds:300}") long stateTtlSeconds,
            @Value("${styleradar.security.jwt.duration:PT24H}") String jwtDuration,
            @Value("${styleradar.security.redis-local-fallback-enabled:false}") boolean localFallbackEnabled) {

        this.googleOAuthPort = googleOAuthPort;
        this.usuarioClient = usuarioClient;
        this.jwtService = jwtService;
        this.redisTemplate = redisTemplate;
        this.environment = environment;
        this.primaryClientId = extraerPrimerClientId(clientId);
        this.defaultRedirectUri = defaultRedirectUri != null ? defaultRedirectUri.trim() : "";
        this.allowedRedirectUris = parseCsvSet(allowedRedirectUris);
        this.authorizationUri = SecuritySanitizer.validateExternalServiceUrl(
                authorizationUri, environment, "GOOGLE_AUTHORIZATION_URI");
        this.stateTtlSeconds = stateTtlSeconds > 0 ? stateTtlSeconds : 300L;
        this.jwtExpiresInSeconds = parsearJwtDurationSegundos(jwtDuration);
        this.localFallbackEnabled = localFallbackEnabled;
    }

    @Override
    public GoogleAuthUrlResponseDTO generarUrlAutorizacion(String redirectUri) {
        exigirClientIdConfigurado();

        String effectiveRedirectUri = resolverRedirectUriSolicitada(redirectUri);
        validarRedirectUriPermitida(effectiveRedirectUri);

        String state = generarStateCriptografico();
        guardarState(state, effectiveRedirectUri);

        String authorizationUrl = UriComponentsBuilder.fromUriString(authorizationUri)
                .queryParam("client_id", primaryClientId)
                .queryParam("redirect_uri", effectiveRedirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", "openid email profile")
                .queryParam("state", state)
                .queryParam("access_type", "online")
                .queryParam("prompt", "select_account")
                .build()
                .encode()
                .toUriString();

        log.info("URL de autorización Google OAuth2 generada con TTL {}s", stateTtlSeconds);
        return new GoogleAuthUrlResponseDTO(
                authorizationUrl,
                state,
                stateTtlSeconds,
                effectiveRedirectUri);
    }

    @Override
    public LoginResponseDTO autenticarConIdToken(String idToken) {
        exigirClientIdConfigurado();
        GoogleIdentityClaims claims = googleOAuthPort.verificarIdToken(idToken);
        return completarAutenticacionGoogle(claims);
    }

    @Override
    public LoginResponseDTO autenticarConCodigo(String code, String state, String redirectUri) {
        exigirClientIdConfigurado();
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de autorización de Google es obligatorio");
        }
        String effectiveRedirectUri = consumirYValidarState(state, redirectUri);
        String idToken = googleOAuthPort.intercambiarCodigoPorIdToken(code.trim(), effectiveRedirectUri);
        GoogleIdentityClaims claims = googleOAuthPort.verificarIdToken(idToken);
        return completarAutenticacionGoogle(claims);
    }

    private LoginResponseDTO completarAutenticacionGoogle(GoogleIdentityClaims claims) {
        if (claims == null
                || claims.email() == null || claims.email().isBlank()
                || claims.subject() == null || claims.subject().isBlank()) {
            throw new CredencialesIncorrectasException(
                    "El token de identidad de Google no contiene información válida de usuario");
        }
        if (!claims.emailVerified()) {
            log.warn("Autenticación con Google rechazada por correo no verificado: {}",
                    SecuritySanitizer.maskEmail(claims.email()));
            throw new CredencialesIncorrectasException(
                    "Tu cuenta de Google debe tener el correo electrónico verificado");
        }

        String normalizedEmail = SecuritySanitizer.normalizeEmail(claims.email());
        VerificarCredencialesResponseDTO usuario = usuarioClient.resolverUsuarioOAuth(
                normalizedEmail,
                claims.name(),
                PROVEEDOR_GOOGLE,
                claims.subject());

        if (usuario == null || usuario.id() == null) {
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de usuarios no retornó una identidad válida para la sesión Google OAuth2");
        }

        if (!"ACTIVA".equalsIgnoreCase(usuario.estado())) {
            String estado = usuario.estado() != null ? usuario.estado() : "INACTIVA";
            log.warn("Acceso OAuth2 Google denegado por estado={} para {}",
                    SecuritySanitizer.sanitizeForLog(estado),
                    SecuritySanitizer.maskEmail(normalizedEmail));
            throw new CuentaNoActivaException(
                    "Tu cuenta está " + estado + ". Contacta al soporte de StyleRadar.");
        }

        Rol rol = parsearRol(usuario.rol());
        String jwt = jwtService.generarToken(usuario.id(), Set.of(rol));

        log.info("Sesión iniciada con Google OAuth2 para usuarioId={} ({})",
                usuario.id(),
                SecuritySanitizer.maskEmail(normalizedEmail));

        return new LoginResponseDTO(jwt, "Bearer", jwtExpiresInSeconds, usuario.id(), Set.of(rol));
    }

    private String resolverRedirectUriSolicitada(String requestedRedirectUri) {
        if (requestedRedirectUri != null && !requestedRedirectUri.isBlank()) {
            return requestedRedirectUri.trim();
        }
        if (defaultRedirectUri.isBlank()) {
            throw new IllegalArgumentException(
                    "No se especificó redirectUri ni existe GOOGLE_REDIRECT_URI configurado");
        }
        return defaultRedirectUri;
    }

    private void validarRedirectUriPermitida(String redirectUri) {
        if (!allowedRedirectUris.contains(redirectUri)) {
            log.warn("Intento de redirectUri no autorizada rechazado en flujo Google OAuth2 (OWASP A01:2025)");
            throw new IllegalArgumentException(
                    "La redirectUri solicitada no está autorizada para autenticación con Google OAuth2");
        }
    }

    private String generarStateCriptografico() {
        byte[] bytes = new byte[STATE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void guardarState(String state, String effectiveRedirectUri) {
        String key = KEY_OAUTH_STATE + state;
        try {
            if (redisTemplate == null) {
                throw new org.springframework.data.redis.RedisConnectionFailureException(
                        "StringRedisTemplate no configurado");
            }
            redisTemplate.opsForValue().set(key, effectiveRedirectUri, stateTtlSeconds, TimeUnit.SECONDS);
        } catch (DataAccessException ex) {
            if (isLocalFallbackPermitted()) {
                log.warn("Respaldo local dev activo para estado Google OAuth2 ante indisponibilidad de Redis");
                long expiresAt = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(stateTtlSeconds);
                localStateStore.put(key, new LocalStateEntry(effectiveRedirectUri, expiresAt));
                return;
            }
            log.error("Fallo fail-closed almacenando estado OAuth2 en Redis: {}",
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "El almacén de seguridad Redis para OAuth2 no está disponible temporalmente", ex);
        }
    }

    private String consumirYValidarState(String state, String requestedRedirectUri) {
        if (state == null || state.isBlank()) {
            throw new CredencialesIncorrectasException(
                    "El parámetro state anti-CSRF de Google OAuth2 es obligatorio");
        }
        String sanitizedRequestedRedirect =
                (requestedRedirectUri != null && !requestedRedirectUri.isBlank())
                        ? requestedRedirectUri.trim()
                        : null;
        if (sanitizedRequestedRedirect != null) {
            validarRedirectUriPermitida(sanitizedRequestedRedirect);
        }

        String key = KEY_OAUTH_STATE + state.trim();
        String storedRedirectUri = obtenerYEliminarState(key);

        if (storedRedirectUri == null || storedRedirectUri.isBlank()) {
            throw new CredencialesIncorrectasException(
                    "El parámetro state de Google OAuth2 es inválido, expiró o ya fue utilizado");
        }
        validarRedirectUriPermitida(storedRedirectUri);

        if (sanitizedRequestedRedirect != null && !storedRedirectUri.equals(sanitizedRequestedRedirect)) {
            throw new CredencialesIncorrectasException(
                    "La URI de redirección no coincide con la sesión OAuth2 iniciada");
        }
        return storedRedirectUri;
    }

    private String obtenerYEliminarState(String key) {
        try {
            if (redisTemplate == null) {
                throw new org.springframework.data.redis.RedisConnectionFailureException(
                        "StringRedisTemplate no configurado");
            }
            return redisTemplate.opsForValue().getAndDelete(key);
        } catch (DataAccessException ex) {
            if (isLocalFallbackPermitted()) {
                log.warn("Respaldo local dev activo consumiendo estado Google OAuth2 ante indisponibilidad de Redis");
                LocalStateEntry entry = localStateStore.remove(key);
                if (entry == null || entry.expiresAtMillis() < System.currentTimeMillis()) {
                    return null;
                }
                return entry.redirectUri();
            }
            log.error("Fallo fail-closed consumiendo estado OAuth2 en Redis: {}",
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "El almacén de seguridad Redis para OAuth2 no está disponible temporalmente", ex);
        }
    }

    private boolean isLocalFallbackPermitted() {
        if (!localFallbackEnabled) {
            return false;
        }
        if (environment == null) {
            return true;
        }
        return SecuritySanitizer.isDevProfileOnly(environment);
    }

    private void exigirClientIdConfigurado() {
        if (primaryClientId.isBlank()) {
            throw new ServicioExternoNoDisponibleException(
                    "La autenticación con Google OAuth2 no está configurada en este entorno (GOOGLE_CLIENT_ID faltante)");
        }
    }

    private static Rol parsearRol(String rawRol) {
        if (rawRol == null || rawRol.isBlank()) {
            throw new CredencialesIncorrectasException("La cuenta de usuario no tiene un rol asignado");
        }
        try {
            return Rol.valueOf(rawRol.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new CredencialesIncorrectasException(
                    "El rol asignado a la cuenta no es válido para autenticación");
        }
    }

    private static String extraerPrimerClientId(String rawClientIds) {
        if (rawClientIds == null || rawClientIds.isBlank()) {
            return "";
        }
        return Arrays.stream(rawClientIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .findFirst()
                .orElse("");
    }

    private static Set<String> parseCsvSet(String rawCsv) {
        if (rawCsv == null || rawCsv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(rawCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static long parsearJwtDurationSegundos(String rawDuration) {
        if (rawDuration == null || rawDuration.isBlank()) {
            throw new IllegalArgumentException("Duración JWT inválida en styleradar.security.jwt.duration");
        }
        try {
            return Duration.parse(rawDuration.trim()).toSeconds();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Duración JWT inválida en styleradar.security.jwt.duration", ex);
        }
    }
}

