package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * Credenciales inválidas o token de sesión no válido (HTTP 401). Nunca revela si el correo existe (RN-04).
 */
public class CredencialesIncorrectasException extends AuthDomainException {

    public CredencialesIncorrectasException(String message) {
        super(message, CodigoError.INVALID_CREDENTIALS);
    }

    public CredencialesIncorrectasException(String message, CodigoError codigo) {
        super(message, codigo);
    }

    public static CredencialesIncorrectasException tokenInvalido(String message) {
        return new CredencialesIncorrectasException(message, CodigoError.INVALID_TOKEN);
    }
}
