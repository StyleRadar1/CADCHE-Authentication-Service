package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * La cuenta existe pero está Inactiva o Bloqueada (RN-03, HTTP 403).
 */
public class CuentaNoActivaException extends AuthDomainException {

    public CuentaNoActivaException(String message) {
        super(message, CodigoError.CUENTA_NO_ACTIVA);
    }
}
