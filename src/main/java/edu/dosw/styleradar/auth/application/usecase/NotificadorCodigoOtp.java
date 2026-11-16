package edu.dosw.styleradar.auth.application.usecase;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import lombok.extern.slf4j.Slf4j;

/**
 * Envía el código OTP por correo con compensación: si el correo falla, se anula la verificación
 * para no dejar un código válido que el usuario nunca recibió (consistencia tipo ACID entre Redis y SMTP).
 */
@Slf4j
final class NotificadorCodigoOtp {

    private final EmailService emailService;
    private final OtpService otpService;

    NotificadorCodigoOtp(EmailService emailService, OtpService otpService) {
        this.emailService = emailService;
        this.otpService = otpService;
    }

    void enviar(String email, String codigo) {
        try {
            emailService.enviarOtp(email, codigo);
        } catch (RuntimeException exception) {
            log.error("Fallo al enviar correo con OTP para {}: {}",
                    SecuritySanitizer.maskEmail(email), SecuritySanitizer.sanitizeForLog(exception.getMessage()));
            otpService.eliminarOtp(email);
            throw exception;
        }
    }
}
