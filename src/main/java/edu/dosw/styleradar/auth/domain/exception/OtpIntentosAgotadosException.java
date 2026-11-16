package edu.dosw.styleradar.auth.domain.exception;

import edu.dosw.styleradar.auth.domain.model.CodigoError;

/**
 * Se agotaron los intentos del código ({@code OTP_INTENTOS_AGOTADOS}) o los reenvíos permitidos
 * ({@code REENVIOS_AGOTADOS}). HTTP 429.
 */
public class OtpIntentosAgotadosException extends AuthDomainException {

    private OtpIntentosAgotadosException(String message, CodigoError codigo, Integer intentos, Long esperar) {
        super(message, codigo, intentos, esperar, null);
    }

    public static OtpIntentosAgotadosException intentosAgotados(long segundosBloqueo) {
        return new OtpIntentosAgotadosException(
                "Se agotaron los intentos. Espere " + segundosBloqueo + " segundos y solicite un nuevo código.",
                CodigoError.OTP_INTENTOS_AGOTADOS, 0, segundosBloqueo);
    }

    public static OtpIntentosAgotadosException reenviosAgotados() {
        return new OtpIntentosAgotadosException(
                "Se superó el máximo de reenvíos permitidos para esta sesión. Inicie sesión nuevamente.",
                CodigoError.REENVIOS_AGOTADOS, null, null);
    }
}
