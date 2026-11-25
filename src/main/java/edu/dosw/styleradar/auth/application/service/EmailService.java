package edu.dosw.styleradar.auth.application.service;

public interface EmailService {

    void enviarOtp(String email, String codigo);

    /** Envía el código de 6 dígitos para restablecer la contraseña olvidada. */
    void enviarCodigoRestablecimiento(String email, String codigo);

    /** Avisa al titular que su contraseña acaba de cambiar (OWASP Forgot Password Cheat Sheet). */
    void enviarAvisoContrasenaCambiada(String email);
}
