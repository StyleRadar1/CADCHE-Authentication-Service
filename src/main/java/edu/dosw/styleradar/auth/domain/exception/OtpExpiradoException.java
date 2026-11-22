package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * El código venció o ya fue anulado ({@code OTP_EXPIRED}), o no existe una verificación en curso
 * ({@code OTP_SESSION_NOT_FOUND}).
 */
public class OtpExpiradoException extends AuthDomainException {

    public OtpExpiradoException(String message) {
        super(message, CodigoError.OTP_EXPIRED);
    }

    public OtpExpiradoException(String message, CodigoError codigo) {
        super(message, codigo);
    }

    public static OtpExpiradoException sesionInexistente() {
        return new OtpExpiradoException(
                "No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.",
                CodigoError.OTP_SESSION_NOT_FOUND);
    }
}
