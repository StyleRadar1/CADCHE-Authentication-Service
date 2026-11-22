package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * Código OTP incorrecto con intentos disponibles ({@code OTP_INCORRECT}, HTTP 401).
 */
public class OtpInvalidoException extends AuthDomainException {

    public OtpInvalidoException(int intentosRestantes) {
        super("El código OTP es incorrecto. Intentos restantes: " + intentosRestantes,
                CodigoError.OTP_INCORRECT, intentosRestantes, null, null);
    }
}
