package edu.dosw.styleradar.auth.domain.model;

/**
 * Resultado de pedir el restablecimiento de contraseña. Es igual exista o no la cuenta, para no revelar
 * qué correos están registrados (OWASP Forgot Password Cheat Sheet).
 */
public record SolicitudRestablecimiento(String email, long expiraEnSegundos) {
}
