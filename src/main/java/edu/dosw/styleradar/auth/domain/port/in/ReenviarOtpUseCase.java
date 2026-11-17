package edu.dosw.styleradar.auth.domain.port.in;

import edu.dosw.styleradar.auth.domain.model.DesafioOtp;

/**
 * SR-RF-92 · Pedir otro código OTP dentro de la verificación en curso.
 */
public interface ReenviarOtpUseCase {

    DesafioOtp reenviar(String email);
}
