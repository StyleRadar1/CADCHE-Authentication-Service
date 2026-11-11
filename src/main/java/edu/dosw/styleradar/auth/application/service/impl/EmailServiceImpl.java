package edu.dosw.styleradar.auth.application.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;
    private final boolean devConsoleFallbackEnabled;
    private final Environment environment;

    @Value("${spring.mail.username:styleradar1@gmail.com}")
    private String correoEmisor;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this(mailSender, false, null);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring e inyectadas por constructor.")
    @Autowired
    public EmailServiceImpl(
            JavaMailSender mailSender,
            @Value("${styleradar.mail.dev-console-fallback-enabled:false}") boolean devConsoleFallbackEnabled,
            Environment environment) {
        this.mailSender = mailSender;
        this.devConsoleFallbackEnabled = devConsoleFallbackEnabled;
        this.environment = environment;
    }

    @PostConstruct
    public void verificarConfiguracion() {
        log.info("Configuración SMTP inicializada. Correo emisor: {}, JavaMailSender disponible: {}",
                SecuritySanitizer.sanitizeForLog(correoEmisor), mailSender != null);
    }

    @Override
    public void enviarOtp(String email, String codigo) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String maskedEmail = SecuritySanitizer.maskEmail(normalizedEmail);

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(correoEmisor);
        mensaje.setTo(normalizedEmail);
        mensaje.setSubject("Código de verificación - StyleRadar");
        mensaje.setText(
                "Hola,\n\n" +
                        "Tu código de verificación para iniciar sesión en StyleRadar es: " +
                        codigo + "\n\n" +
                        "Este código tiene una validez de 5 minutos.\n" +
                        "No compartas este código con otras personas.\n\n" +
                        "Equipo StyleRadar"
        );

        try {
            mailSender.send(mensaje);
            log.info("Correo con código OTP enviado exitosamente a {}", maskedEmail);
        } catch (MailException ex) {
            if (isDevConsoleFallbackActive()) {
                log.warn("Servidor SMTP no disponible en entorno dev ({}). Código OTP solo para desarrollo local de {}: {}",
                        SecuritySanitizer.sanitizeForLog(ex.getMessage()), maskedEmail, SecuritySanitizer.sanitizeForLog(codigo));
                return;
            }
            log.error("Fallo fail-closed enviando OTP por SMTP a {}: {}",
                    maskedEmail, SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "No fue posible enviar el código de verificación por correo electrónico", ex);
        }
    }

    private boolean isDevConsoleFallbackActive() {
        return devConsoleFallbackEnabled && SecuritySanitizer.isDevProfileOnly(environment);
    }
}
