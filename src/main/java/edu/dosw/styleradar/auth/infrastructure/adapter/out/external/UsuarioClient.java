package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import java.net.URI;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import edu.dosw.styleradar.auth.application.dto.request.VerificarCredencialesRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public final class UsuarioClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final boolean devFallbackEnabled;
    private final Environment environment;

    public UsuarioClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String username,
            String password) {
        this(restClientBuilder, baseUrl, username, password, false, null);
    }

    public UsuarioClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String username,
            String password,
            boolean devFallbackEnabled) {
        this(restClientBuilder, baseUrl, username, password, devFallbackEnabled, null);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring e inyectadas por constructor.")
    @Autowired
    public UsuarioClient(
            RestClient.Builder restClientBuilder,
            @Value("${styleradar.usuarios.base-url}") String baseUrl,
            @Value("${styleradar.usuarios.username}") String username,
            @Value("${styleradar.usuarios.password}") String password,
            @Value("${styleradar.usuarios.dev-fallback-enabled:false}") boolean devFallbackEnabled,
            Environment environment) {

        this.baseUrl = validarUrlExterna(baseUrl, environment);
        this.devFallbackEnabled = devFallbackEnabled;
        this.environment = environment;
        this.restClient = restClientBuilder
                .baseUrl(this.baseUrl)
                .defaultHeaders(headers ->
                        headers.setBasicAuth(username, password))
                .build();
    }

    public VerificarCredencialesResponseDTO verificarCredenciales(
            String email, String password) {

        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        VerificarCredencialesRequestDTO request =
                new VerificarCredencialesRequestDTO(normalizedEmail, password);

        try {
            return restClient.post()
                    .uri("/internal/api/v1/usuarios/verificar-credenciales")
                    .body(request)
                    .retrieve()
                    .body(VerificarCredencialesResponseDTO.class);
        } catch (ResourceAccessException exception) {
            if (isDevFallbackActive()) {
                log.warn("No fue posible conectar con ms-usuarios en {}. Usando respaldo local dev para {}",
                        SecuritySanitizer.sanitizeForLog(baseUrl),
                        SecuritySanitizer.maskEmail(normalizedEmail));
                return fallbackVerificarCredencialesDev(normalizedEmail, password);
            }
            log.error("Fallo fail-closed conectando con ms-usuarios en {}: {}",
                    SecuritySanitizer.sanitizeForLog(baseUrl),
                    SecuritySanitizer.sanitizeForLog(exception.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de verificación de usuarios no está disponible temporalmente", exception);
        }
    }

    private String validarUrlExterna(String rawUrl, Environment env) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("La URL del servicio de usuarios es obligatoria");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("URL de ms-usuarios inválida (OWASP A01:2025 / SSRF)", ex);
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Esquema no permitido en USUARIOS_SERVICE_URL (solo http/https)");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("No se permite incluir credenciales en USUARIOS_SERVICE_URL");
        }
        if (host.isEmpty()
                || host.startsWith("169.254.")
                || "metadata.google.internal".equals(host)) {
            throw new IllegalArgumentException("Host bloqueado por prevención SSRF en USUARIOS_SERVICE_URL");
        }
        if (SecuritySanitizer.isProdProfile(env) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("En perfil prod USUARIOS_SERVICE_URL debe utilizar HTTPS");
        }
        return rawUrl.trim();
    }

    private boolean isDevFallbackActive() {
        if (!devFallbackEnabled) {
            return false;
        }
        if (environment == null) {
            return true;
        }
        return SecuritySanitizer.isDevProfileOnly(environment);
    }

    private VerificarCredencialesResponseDTO fallbackVerificarCredencialesDev(String email, String password) {
        if (password == null || password.isBlank()
                || "wrong".equalsIgnoreCase(password)
                || "incorrecta".equalsIgnoreCase(password)) {
            throw new CredencialesIncorrectasException(
                    "El correo o la contraseña son incorrectos (validación local dev)");
        }
        String rol = "COMPRADOR";
        String lower = email.toLowerCase(Locale.ROOT);
        if (lower.contains("admin")) {
            rol = "ADMIN_STYLERADAR";
        } else if (lower.contains("almacen")) {
            rol = "ADMIN_ALMACEN";
        } else if (lower.contains("fundacion")) {
            rol = "REPRESENTANTE_FUNDACION";
        }
        return new VerificarCredencialesResponseDTO(10L, email, rol, "ACTIVA");
    }
}
