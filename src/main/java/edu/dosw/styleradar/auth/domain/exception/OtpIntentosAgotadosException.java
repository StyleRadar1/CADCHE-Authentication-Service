package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * Se agotaron los intentos del código ({@code OTP_ATTEMPTS_EXHAUSTED}) o los reenvíos permitidos
 * ({@code RESENDS_EXHAUSTED}). HTTP 429. En los reenvíos agotados no hay espera: {@code esperarSegundos} y
 * {@code Retry-After} son 0 porque el usuario debe iniciar sesión de nuevo.
 */
public class OtpIntentosAgotadosException extends AuthDomainException {

    private OtpIntentosAgotadosException(String message, CodigoError codigo, Integer intentos, Long esperar) {
        super(message, codigo, intentos, esperar, null);
    }

    public static OtpIntentosAgotadosException intentosAgotados(long segundosBloqueo) {
        return new OtpIntentosAgotadosException(
                "Se agotaron los intentos. Espere " + segundosBloqueo + " segundos y solicite un nuevo código.",
                CodigoError.OTP_ATTEMPTS_EXHAUSTED, 0, segundosBloqueo);
    }

    public static OtpIntentosAgotadosException reenviosAgotados() {
        return new OtpIntentosAgotadosException(
                "Se superó el máximo de reenvíos permitidos para esta sesión. Inicie sesión nuevamente.",
                CodigoError.RESENDS_EXHAUSTED, null, 0L);
    }
}
