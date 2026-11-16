package edu.dosw.styleradar.auth.infrastructure.security;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Utilidades centralizadas de sanitización de entradas, prevención de Log Forging
 * (CRLF Injection - OWASP A05:2025), enmascaramiento de PII en trazas (OWASP A09:2025)
 * y verificación segura de perfiles de ejecución.
 */
public final class SecuritySanitizer {

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\r\\n\\t\\p{Cntrl}]");

    private SecuritySanitizer() {
    }

    /**
     * Neutraliza caracteres de control (CR, LF, TAB) antes de registrar valores en logs o respuestas.
     */
    public static String sanitizeForLog(String value) {
        if (value == null) {
            return "[null]";
        }
        return CONTROL_CHARS.matcher(value).replaceAll("_");
    }

    /**
     * Normaliza el correo electrónico (trim + minúsculas) y rechaza caracteres de control
     * para prevenir inyección de claves Redis o cabeceras SMTP (OWASP A05:2025).
     */
    public static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("El correo electrónico es obligatorio");
        }
        if (CONTROL_CHARS.matcher(email).find()) {
            throw new IllegalArgumentException("El correo electrónico contiene caracteres no permitidos");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Enmascara el usuario local del correo para evitar exposición de PII en logs (OWASP A09:2025).
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        String sanitized = sanitizeForLog(email.trim());
        int atIndex = sanitized.indexOf('@');
        if (atIndex <= 0 || atIndex == sanitized.length() - 1) {
            return "***";
        }
        String local = sanitized.substring(0, atIndex);
        String domain = sanitized.substring(atIndex);
        int visibleChars = Math.min(2, local.length());
        return local.substring(0, visibleChars) + "***" + domain;
    }

    /**
     * Devuelve {@code true} únicamente cuando el perfil activo incluye {@code dev}
     * y excluye estrictamente {@code qa} y {@code prod}.
     */
    public static boolean isDevProfileOnly(Environment environment) {
        return environment != null
                && environment.acceptsProfiles(Profiles.of("dev"))
                && !environment.acceptsProfiles(Profiles.of("prod", "qa"));
    }

    /**
     * Devuelve {@code true} cuando el perfil {@code prod} está activo.
     */
    public static boolean isProdProfile(Environment environment) {
        return environment != null && environment.acceptsProfiles(Profiles.of("prod"));
    }

    /**
     * Valida una URL de servicio o proveedor externo contra ataques SSRF (OWASP A01:2025):
     * restringe esquema a {@code http}/{@code https}, prohíbe credenciales embebidas,
     * bloquea metadatos cloud y exige {@code https} en perfil {@code prod}.
     */
    public static String validateExternalServiceUrl(
            String rawUrl, Environment environment, String parameterName) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("La URL " + parameterName + " es obligatoria");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("URL inválida en " + parameterName + " (OWASP A01:2025 / SSRF)", ex);
        }

        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";

        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Esquema no permitido en " + parameterName + " (solo http/https)");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("No se permite incluir credenciales en " + parameterName);
        }
        if (host.isEmpty()
                || host.startsWith("169.254.")
                || "metadata.google.internal".equals(host)) {
            throw new IllegalArgumentException("Host bloqueado por prevención SSRF en " + parameterName);
        }
        if (isProdProfile(environment) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("En perfil prod " + parameterName + " debe utilizar HTTPS");
        }
        return rawUrl.trim();
    }
}
