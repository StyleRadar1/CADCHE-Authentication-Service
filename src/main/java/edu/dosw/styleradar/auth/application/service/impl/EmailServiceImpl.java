package edu.dosw.styleradar.auth.application.service.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
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
            @Autowired(required = false) JavaMailSender mailSender,
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
        enviar(email, "Código de verificación - StyleRadar",
                "Hola,\n\n"
                        + "Tu código de verificación para iniciar sesión en StyleRadar es: " + codigo + "\n\n"
                        + "Este código tiene una validez de 5 minutos.\n"
                        + "No compartas este código con otras personas.\n\n"
                        + "Equipo StyleRadar",
                codigo, "OTP");
    }

    @Override
    public void enviarCodigoRestablecimiento(String email, String codigo) {
        enviar(email, "Restablecer tu contraseña - StyleRadar",
                "Hola,\n\n"
                        + "Recibimos una solicitud para restablecer la contraseña de tu cuenta en StyleRadar.\n"
                        + "Tu código es: " + codigo + "\n\n"
                        + "Este código tiene una validez de 5 minutos y solo sirve una vez.\n"
                        + "Si no fuiste tú, ignora este correo: tu contraseña no cambiará.\n\n"
                        + "Equipo StyleRadar",
                codigo, "restablecimiento de contraseña");
    }

    @Override
    public void enviarAvisoContrasenaCambiada(String email) {
        enviar(email, "Tu contraseña cambió - StyleRadar",
                "Hola,\n\n"
                        + "La contraseña de tu cuenta en StyleRadar se cambió correctamente y cerramos todas tus sesiones abiertas.\n"
                        + "Si no fuiste tú, restablece la contraseña de inmediato y escríbenos a styleradar1@gmail.com.\n\n"
                        + "Equipo StyleRadar",
                null, "aviso de cambio de contraseña");
    }

    private void enviar(String email, String asunto, String cuerpo, String codigoDev, String tipo) {
        String normalizedEmail = SecuritySanitizer.normalizeEmail(email);
        String maskedEmail = SecuritySanitizer.maskEmail(normalizedEmail);

        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(correoEmisor);
        mensaje.setTo(normalizedEmail);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);

        try {
            if (mailSender == null) {
                throw new MailSendException("JavaMailSender no configurado");
            }
            mailSender.send(mensaje);
            log.info("Correo de {} enviado exitosamente a {}", tipo, maskedEmail);
        } catch (MailException ex) {
            if (isDevConsoleFallbackActive()) {
                log.warn("Servidor SMTP no disponible en entorno dev ({}). Correo de {} solo para desarrollo local de {}{}",
                        SecuritySanitizer.sanitizeForLog(ex.getMessage()), tipo, maskedEmail,
                        codigoDev == null ? "" : ": " + SecuritySanitizer.sanitizeForLog(codigoDev));
                return;
            }
            log.error("Fallo fail-closed enviando correo de {} por SMTP a {}: {}",
                    tipo, maskedEmail, SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "No fue posible enviar el código de verificación por correo electrónico", ex);
        }
    }

    private boolean isDevConsoleFallbackActive() {
        return devConsoleFallbackEnabled && SecuritySanitizer.isDevProfileOnly(environment);
    }
}
