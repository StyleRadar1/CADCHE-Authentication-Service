package edu.dosw.styleradar.auth.domain.port.in;

import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;

/**
 * SR-RF-92 · Verificar el correo con el código OTP y emitir la sesión.
 */
public interface VerificarOtpUseCase {

    SesionAutenticada verificar(String email, String codigoOtp);
}
