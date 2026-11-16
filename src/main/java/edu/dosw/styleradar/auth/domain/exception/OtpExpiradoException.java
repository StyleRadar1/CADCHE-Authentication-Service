package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * El código venció o ya fue anulado ({@code OTP_VENCIDO}), o no existe una verificación en curso
 * ({@code SESION_OTP_INEXISTENTE}).
 */
public class OtpExpiradoException extends AuthDomainException {

    public OtpExpiradoException(String message) {
        super(message, CodigoError.OTP_VENCIDO);
    }

    public OtpExpiradoException(String message, CodigoError codigo) {
        super(message, codigo);
    }

    public static OtpExpiradoException sesionInexistente() {
        return new OtpExpiradoException(
                "No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.",
                CodigoError.SESION_OTP_INEXISTENTE);
    }
}
