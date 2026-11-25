package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

    static final String DEFAULT_CORREO_VERIFICADO_PATH = "/api/v1/internal/users/{id}/email-verification";
    static final String DEFAULT_CREDENCIALES_PATH = "/api/v1/internal/users/verify-credentials";
    static final String DEFAULT_OAUTH2_PATH = "/api/v1/internal/users/oauth2";
    static final String DEFAULT_BUSQUEDA_CORREO_PATH = "/api/v1/internal/users/lookup";
    static final String DEFAULT_CONTRASENA_PATH = "/api/v1/internal/users/{id}/password";

    private final RestClient restClient;
    private final String baseUrl;
    private final String oauth2Path;
    private final String correoVerificadoPath;
    private final String credencialesPath;
    private final String busquedaCorreoPath;
    private final String contrasenaPath;
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
                DEFAULT_OAUTH2_PATH, DEFAULT_CORREO_VERIFICADO_PATH, DEFAULT_CREDENCIALES_PATH);
    }

    public UsuarioClient(
            RestClient.Builder restClientBuilder,
            String baseUrl,
            String username,
            String password,
            boolean devFallbackEnabled,
            Environment environment,
            String oauth2Path,
            String correoVerificadoPath,
            String credencialesPath) {
        this(restClientBuilder, baseUrl, username, password, devFallbackEnabled, environment,
                oauth2Path, correoVerificadoPath, credencialesPath, DEFAULT_BUSQUEDA_CORREO_PATH, DEFAULT_CONTRASENA_PATH);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring e inyectadas por constructor.")
    @SuppressWarnings("java:S107")
    @Autowired
    public UsuarioClient(
            RestClient.Builder restClientBuilder,
            @Value("${styleradar.usuarios.base-url}") String baseUrl,
            @Value("${styleradar.usuarios.username}") String username,
            @Value("${styleradar.usuarios.password}") String password,
            @Value("${styleradar.usuarios.dev-fallback-enabled:false}") boolean devFallbackEnabled,
            Environment environment,
            @Value("${styleradar.usuarios.oauth2-path:" + DEFAULT_OAUTH2_PATH + "}") String oauth2Path,
            @Value("${styleradar.usuarios.correo-verificado-path:" + DEFAULT_CORREO_VERIFICADO_PATH + "}")
            String correoVerificadoPath,
            @Value("${styleradar.usuarios.credenciales-path:" + DEFAULT_CREDENCIALES_PATH + "}")
            String credencialesPath,
            @Value("${styleradar.usuarios.busqueda-correo-path:" + DEFAULT_BUSQUEDA_CORREO_PATH + "}")
            String busquedaCorreoPath,
            @Value("${styleradar.usuarios.contrasena-path:" + DEFAULT_CONTRASENA_PATH + "}")
            String contrasenaPath) {

        this.baseUrl = SecuritySanitizer.validateExternalServiceUrl(
                baseUrl, environment, "USUARIOS_SERVICE_URL");
        this.oauth2Path = (oauth2Path == null || oauth2Path.isBlank())
                ? DEFAULT_OAUTH2_PATH
                : oauth2Path.trim();
        this.correoVerificadoPath = (correoVerificadoPath == null || correoVerificadoPath.isBlank())
                ? DEFAULT_CORREO_VERIFICADO_PATH
                : correoVerificadoPath.trim();
        this.credencialesPath = (credencialesPath == null || credencialesPath.isBlank())
                ? DEFAULT_CREDENCIALES_PATH
                : credencialesPath.trim();
        this.busquedaCorreoPath = (busquedaCorreoPath == null || busquedaCorreoPath.isBlank())
                ? DEFAULT_BUSQUEDA_CORREO_PATH
                : busquedaCorreoPath.trim();
        this.contrasenaPath = (contrasenaPath == null || contrasenaPath.isBlank())
                ? DEFAULT_CONTRASENA_PATH
                : contrasenaPath.trim();
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
            return postCredenciales(credencialesPath, request);
        } catch (HttpClientErrorException.Unauthorized exception) {
            log.warn("Credenciales incorrectas para: {}", SecuritySanitizer.maskEmail(normalizedEmail));
            throw new CredencialesIncorrectasException("El correo o la contraseña son incorrectos");
        } catch (HttpClientErrorException.Forbidden exception) {
            if (esErrorDeCuentaNoActiva(exception)) {
                log.warn("Cuenta no activa para: {}", SecuritySanitizer.maskEmail(normalizedEmail));
                throw new CuentaNoActivaException("La cuenta se encuentra inactiva o bloqueada");
            }
            // ms-usuarios informa las cuentas inactivas con 200 y su estado; un 403 sin ese motivo es su seguridad
            // rechazando a ms-auth (ruta bloqueada o credenciales internas sin rol de servicio), no la cuenta.
            log.error("ms-usuarios rechazó la llamada de servicio (HTTP 403) en {}: revisar la ruta interna y "
                    + "USUARIOS_INTERNAL_USERNAME/USUARIOS_INTERNAL_PASSWORD", credencialesPath);
            throw new ServicioExternoNoDisponibleException(
                    "No fue posible verificar las credenciales con el servicio de usuarios", exception);
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
        if (sanitizedName.isBlank()) {
            // ms-usuarios exige name; si Google no lo envía se usa la parte local del correo.
            sanitizedName = normalizedEmail.substring(0, normalizedEmail.indexOf('@') > 0 ? normalizedEmail.indexOf('@') : normalizedEmail.length());
        }
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
            patchCorreoVerificado(correoVerificadoPath, usuarioId);
            log.info("Notificación enviada a ms-usuarios: correo verificado para usuario ID {}", usuarioId);
        } catch (RestClientException ex) {
            log.error("No fue posible marcar el correo como verificado en ms-usuarios para usuario ID {}: {}",
                    usuarioId, SecuritySanitizer.sanitizeForLog(ex.getMessage()));
        }
    }

    /**
     * Busca la cuenta por correo (contrato U6). {@code 404} significa que no existe y devuelve vacío;
     * cualquier otro error es fail-closed ({@code 503}). El correo viaja en el cuerpo, no en la URL,
     * para que no quede en los registros de acceso.
     */
    public Optional<VerificarCredencialesResponseDTO> buscarPorCorreo(String email) {
        String correo = SecuritySanitizer.normalizeEmail(email);
        try {
            return Optional.ofNullable(restClient.post()
                    .uri(busquedaCorreoPath)
                    .body(Map.of("email", correo))
                    .retrieve()
                    .body(VerificarCredencialesResponseDTO.class));
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            log.error("Fallo consultando la cuenta de {} en ms-usuarios: {}",
                    SecuritySanitizer.maskEmail(correo), SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de usuarios no está disponible temporalmente", ex);
        }
    }

    /**
     * Reemplaza la contraseña en ms-usuarios (contrato U7). La contraseña viaja solo por el canal interno
     * con TLS y Basic; nunca se registra en logs. {@code 403}/{@code 404} significan que la cuenta ya no
     * está activa.
     */
    public void cambiarContrasena(Long usuarioId, String nuevaContrasena) {
        try {
            restClient.put()
                    .uri(contrasenaPath, usuarioId)
                    .body(Map.of("newPassword", nuevaContrasena))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Contraseña restablecida en ms-usuarios para usuario ID {}", usuarioId);
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.NotFound ex) {
            log.warn("ms-usuarios rechazó el cambio de contraseña del usuario ID {} con estado HTTP {}",
                    usuarioId, ex.getStatusCode().value());
            throw new CuentaNoActivaException("La cuenta se encuentra inactiva o bloqueada");
        } catch (RestClientException ex) {
            log.error("Fallo cambiando la contraseña del usuario ID {} en ms-usuarios: {}",
                    usuarioId, SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "El servicio de usuarios no está disponible temporalmente", ex);
        }
    }

    private VerificarCredencialesResponseDTO postCredenciales(String ruta, VerificarCredencialesRequestDTO request) {
        return restClient.post()
                .uri(ruta)
                .body(request)
                .retrieve()
                .body(VerificarCredencialesResponseDTO.class);
    }

    private void patchCorreoVerificado(String ruta, Long usuarioId) {
        restClient.patch()
                .uri(ruta, usuarioId)
                .retrieve()
                .toBodilessEntity();
    }

    private static boolean esErrorDeCuentaNoActiva(HttpClientErrorException.Forbidden ex) {
        String body = ex.getResponseBodyAsString();
        if (body.isBlank()) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("account_not_active")
                || lower.contains("inactiva")
                || lower.contains("bloqueada")
                || lower.contains("inactive")
                || lower.contains("blocked");
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
            return "STYLERADAR_ADMIN";
        }
        if (lower.contains("almacen")) {
            return "STORE_ADMIN";
        }
        if (lower.contains("fundacion")) {
            return "FOUNDATION_REPRESENTATIVE";
        }
        return "BUYER";
    }
}
