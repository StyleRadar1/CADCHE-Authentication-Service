package edu.dosw.styleradar.auth.application.usecase;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.model.DesafioOtp;
import edu.dosw.styleradar.auth.domain.port.in.ReenviarOtpUseCase;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import lombok.extern.slf4j.Slf4j;

/**
 * SR-RF-92 · Pedir otro código. Máximo 3 reenvíos por verificación; bloqueado 60 s tras 3 fallos (D1).
 */
@Slf4j
public class ReenviarOtpUseCaseImpl implements ReenviarOtpUseCase {

    private final OtpService otpService;
    private final NotificadorCodigoOtp notificador;

    public ReenviarOtpUseCaseImpl(OtpService otpService, EmailService emailService) {
        this.otpService = otpService;
        this.notificador = new NotificadorCodigoOtp(emailService, otpService);
    }

    @Override
    public DesafioOtp reenviar(String email) {
        String correo = SecuritySanitizer.normalizeEmail(email);
        log.info("Solicitud de reenvío de OTP para correo: {}", SecuritySanitizer.maskEmail(correo));

        String codigo = otpService.reenviarOtp(correo);
        notificador.enviar(correo, codigo);

        return new DesafioOtp(
                correo,
                otpService.obtenerVigenciaOtpSegundos(),
                otpService.obtenerMaxIntentos(),
                otpService.obtenerReenviosRestantes(correo));
    }
}
