package edu.dosw.styleradar.auth.infrastructure.security;

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
}
