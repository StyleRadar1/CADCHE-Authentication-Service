package edu.dosw.styleradar.auth.infrastructure.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("SecuritySanitizer: logs, correos, perfiles y prevención SSRF (OWASP A01/A05/A09)")
class SecuritySanitizerTest {

    private static MockEnvironment perfiles(String... activos) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(activos);
        return env;
    }

    @Test
    void sanitizeForLog_neutralizaControlesYNull() {
        assertEquals("[null]", SecuritySanitizer.sanitizeForLog(null));
        assertEquals("a_b_c", SecuritySanitizer.sanitizeForLog("a\nb\tc"));
        assertEquals("limpio", SecuritySanitizer.sanitizeForLog("limpio"));
    }

    @Test
    void normalizeEmail_recortaYPasaAMinusculas() {
        assertEquals("ana@styleradar.com", SecuritySanitizer.normalizeEmail("  Ana@StyleRadar.com "));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "ana@x.com\r\nBcc: otro@x.com"})
    void normalizeEmail_rechazaVacioOControles(String email) {
        assertThrows(IllegalArgumentException.class, () -> SecuritySanitizer.normalizeEmail(email));
    }

    @Test
    void maskEmail_ocultaUsuarioLocal() {
        assertEquals("an***@styleradar.com", SecuritySanitizer.maskEmail("ana@styleradar.com"));
        assertEquals("a***@x.co", SecuritySanitizer.maskEmail("a@x.co"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "sin-arroba", "@dominio.com", "usuario@"})
    void maskEmail_valoresInvalidosQuedanOcultos(String email) {
        assertEquals("***", SecuritySanitizer.maskEmail(email));
    }

    @Test
    void perfiles_devSoloCuandoNoHayQaNiProd() {
        assertFalse(SecuritySanitizer.isDevProfileOnly(null));
        assertTrue(SecuritySanitizer.isDevProfileOnly(perfiles("dev")));
        assertFalse(SecuritySanitizer.isDevProfileOnly(perfiles("dev", "qa")));
        assertFalse(SecuritySanitizer.isDevProfileOnly(perfiles("qa")));
        assertFalse(SecuritySanitizer.isProdProfile(null));
        assertTrue(SecuritySanitizer.isProdProfile(perfiles("prod")));
        assertFalse(SecuritySanitizer.isProdProfile(perfiles("qa")));
    }

    @Test
    void validateExternalServiceUrl_aceptaHttpFueraDeProdYHttpsSiempre() {
        assertEquals("http://usuarios:8120", SecuritySanitizer.validateExternalServiceUrl(
                " http://usuarios:8120 ", perfiles("dev"), "USUARIOS_URL"));
        assertEquals("https://api.styleradar.com", SecuritySanitizer.validateExternalServiceUrl(
                "https://api.styleradar.com", perfiles("prod"), "USUARIOS_URL"));
        assertEquals("http://usuarios:8120", SecuritySanitizer.validateExternalServiceUrl(
                "http://usuarios:8120", null, "USUARIOS_URL"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            " ",
            "ht tp://espacio",
            "ftp://archivos.styleradar.com",
            "file:///etc/passwd",
            "https://usuario:clave@styleradar.com",
            "https:///sin-host",
            "http://169.254.169.254/latest/meta-data",
            "http://metadata.google.internal/computeMetadata"
    })
    void validateExternalServiceUrl_rechazaUrlsPeligrosas(String url) {
        MockEnvironment dev = perfiles("dev");
        assertThrows(IllegalArgumentException.class,
                () -> SecuritySanitizer.validateExternalServiceUrl(url, dev, "URL"));
    }

    @Test
    void validateExternalServiceUrl_enProdExigeHttps() {
        MockEnvironment prod = perfiles("prod");
        assertThrows(IllegalArgumentException.class,
                () -> SecuritySanitizer.validateExternalServiceUrl("http://usuarios:8120", prod, "URL"));
    }
}
