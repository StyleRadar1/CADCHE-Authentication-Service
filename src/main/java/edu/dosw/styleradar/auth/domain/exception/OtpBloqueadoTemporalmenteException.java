package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * Espera obligatoria tras agotar los intentos del código (decisión D1). Aplica a {@code /otp/resend}
 * y a {@code /login} con correo sin verificar (SR-RF-02 FA-15).
 */
public class OtpBloqueadoTemporalmenteException extends AuthDomainException {

    public OtpBloqueadoTemporalmenteException(long segundosRestantes) {
        super("Debe esperar " + segundosRestantes + " segundos para solicitar un nuevo código.",
                CodigoError.OTP_RESEND_BLOCKED, null, segundosRestantes, null);
    }

    public long getSegundosRestantes() {
        return getEsperarSegundos();
    }
}
