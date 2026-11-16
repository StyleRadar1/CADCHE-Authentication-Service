package edu.dosw.styleradar.auth.domain.exception;

/**
 * Excepción lanzada bajo principio fail-closed (OWASP A10:2025) cuando una
 * dependencia crítica externa (ms-usuarios, Redis o SMTP) no está disponible.
 */
public class ServicioExternoNoDisponibleException extends RuntimeException {

    public ServicioExternoNoDisponibleException(String message) {
        super(message);
    }

    public ServicioExternoNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}

