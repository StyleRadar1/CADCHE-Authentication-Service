package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * Excepción lanzada bajo principio fail-closed (OWASP A10:2025) cuando una
 * dependencia crítica externa (ms-usuarios, Redis, SMTP o Google) no está disponible.
 */
public class ServicioExternoNoDisponibleException extends AuthDomainException {

    public ServicioExternoNoDisponibleException(String message) {
        super(message, CodigoError.SERVICE_UNAVAILABLE);
    }

    public ServicioExternoNoDisponibleException(String message, Throwable cause) {
        super(message, CodigoError.SERVICE_UNAVAILABLE, cause);
    }
}
