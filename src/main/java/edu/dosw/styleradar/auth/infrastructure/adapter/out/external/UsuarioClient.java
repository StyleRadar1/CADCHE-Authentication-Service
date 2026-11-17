package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import java.util.Locale;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import edu.dosw.styleradar.auth.application.dto.request.OAuthUsuarioRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.VerificarCredencialesRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public final class UsuarioClient {

    static final String DEFAULT_CORREO_VERIFICADO_PATH = "/internal/api/v1/usuarios/{id}/verificar-correo";
    static final String DEFAULT_CREDENCIALES_PATH = "/internal/api/v1/usuarios/verificar-credenciales";

    private final RestClient restClient;
    private final String baseUrl;
    private final String oauth2Path;
    private final String correoVerificadoPath;
    private final String credencialesPath;
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

    public UsuarioClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String username,
            String password,
            boolean devFallbackEnabled,
            Environment environment) {
        this(restClientBuilder, baseUrl, username, password, devFallbackEnabled, environment,
                "/internal/api/v1/usuarios/oauth2", DEFAULT_CORREO_VERIFICADO_PATH, DEFAULT_CREDENCIALES_PATH);
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
            Environment environment,
            @Value("${styleradar.usuarios.oauth2-path:/internal/api/v1/usuarios/oauth2}") String oauth2Path,
            @Value("${styleradar.usuarios.correo-verificado-path:" + DEFAULT_CORREO_VERIFICADO_PATH + "}")
            String correoVerificadoPath,
            @Value("${styleradar.usuarios.credenciales-path:" + DEFAULT_CREDENCIALES_PATH + "}")
            String credencialesPath) {

        this.baseUrl = SecuritySanitizer.validateExternalServiceUrl(
                baseUrl, environment, "USUARIOS_SERVICE_URL");
        this.oauth2Path = (oauth2Path == null || oauth2Path.isBlank())
                ? "/internal/api/v1/usuarios/oauth2"
                : oauth2Path.trim();
        this.correoVerificadoPath = (correoVerificadoPath == null || correoVerificadoPath.isBlank())
                ? DEFAULT_CORREO_VERIFICADO_PATH
                : correoVerificadoPath.trim();
        this.credencialesPath = (credencialesPath == null || credencialesPath.isBlank())
                ? DEFAULT_CREDENCIALES_PATH
                : credencialesPath.trim();
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
                    .uri(credencialesPath)
                    .body(request)
                    .retrieve()
                    .body(VerificarCredencialesResponseDTO.class);
        } catch (HttpClientErrorException.Unauthorized exception) {
            log.warn("Credenciales incorrectas para: {}", SecuritySanitizer.maskEmail(normalizedEmail));
            throw new CredencialesIncorrectasException("El correo o la contraseña son incorrectos");
        } catch (HttpClientErrorException.Forbidden exception) {
            log.warn("Cuenta no activa para: {}", SecuritySanitizer.maskEmail(normalizedEmail));
            throw new CuentaNoActivaException("La cuenta se encuentra inactiva o bloqueada");
        } catch (RestClientResponseException exception) {
            log.error("Fallo al verificar credenciales en ms-usuarios (HTTP {}): {}",
                    exception.getStatusCode().value(), SecuritySanitizer.sanitizeForLog(exception.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "No fue posible verificar las credenciales con el servicio de usuarios", exception);
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

    public VerificarCredencialesResponseDTO resolverUsuarioOAuth(
            String email, String nombre, String proveedor, String proveedorId) {

        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String sanitizedName = nombre != null ? SecuritySanitizer.sanitizeForLog(nombre.trim()) : "";
        OAuthUsuarioRequestDTO request = new OAuthUsuarioRequestDTO(
                normalizedEmail, sanitizedName, proveedor, proveedorId);

        try {
            return restClient.post()
                    .uri(oauth2Path)
                    .body(request)
                    .retrieve()
                    .body(VerificarCredencialesResponseDTO.class);
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new CredencialesIncorrectasException(
                    "No autorizado para iniciar sesión mediante proveedor OAuth2");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new CuentaNoActivaException(
                    "La cuenta asociada al correo se encuentra inactiva o bloqueada");
        } catch (RestClientResponseException exception) {
            log.error("Error HTTP {} al resolver usuario OAuth2 {} en ms-usuarios: {}",
                    exception.getStatusCode().value(),
                    SecuritySanitizer.maskEmail(normalizedEmail),
                    SecuritySanitizer.sanitizeForLog(exception.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "No fue posible sincronizar la identidad OAuth2 con el servicio de usuarios", exception);
        } catch (ResourceAccessException exception) {
            if (isDevFallbackActive()) {
                log.warn("Respaldo local dev OAuth2 activo ante indisponibilidad de ms-usuarios para {}",
                        SecuritySanitizer.maskEmail(normalizedEmail));
                long devId = Integer.toUnsignedLong(normalizedEmail.hashCode()) % 9000L + 100L;
                return new VerificarCredencialesResponseDTO(
                        devId, normalizedEmail, inferirRolDev(normalizedEmail), "ACTIVA");
            }
            log.error("Fallo fail-closed resolviendo usuario OAuth2 en {}: {}",
                    SecuritySanitizer.sanitizeForLog(baseUrl),
                    SecuritySanitizer.sanitizeForLog(exception.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de usuarios no está disponible temporalmente para autenticación OAuth2", exception);
        }
    }

    /**
     * Marca el correo como verificado en ms-usuarios (contrato U3). Es tolerante a fallos: el usuario ya
     * demostró la propiedad del correo, así que no se le niega la sesión; el fallo queda en ERROR para
     * reintentar la sincronización (en el siguiente login se le volvería a pedir el código).
     */
    public void marcarCorreoVerificado(Long usuarioId) {
        if (usuarioId == null) {
            return;
        }
        try {
            restClient.patch()
                    .uri(correoVerificadoPath, usuarioId)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Notificación enviada a ms-usuarios: correo verificado para usuario ID {}", usuarioId);
        } catch (RestClientException ex) {
            log.error("No fue posible marcar el correo como verificado en ms-usuarios para usuario ID {}: {}",
                    usuarioId, SecuritySanitizer.sanitizeForLog(ex.getMessage()));
        }
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
        return new VerificarCredencialesResponseDTO(10L, email, inferirRolDev(email), "ACTIVA");
    }

    private static String inferirRolDev(String email) {
        String lower = email.toLowerCase(Locale.ROOT);
        if (lower.contains("admin")) {
            return "ADMIN_STYLERADAR";
        }
        if (lower.contains("almacen")) {
            return "ADMIN_ALMACEN";
        }
        if (lower.contains("fundacion")) {
            return "REPRESENTANTE_FUNDACION";
        }
        return "COMPRADOR";
    }
}
