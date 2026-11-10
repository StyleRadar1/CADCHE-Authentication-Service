package edu.dosw.styleradar.auth.application.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import edu.dosw.styleradar.auth.application.service.EmailService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:styleradar1@gmail.com}")
    private String correoEmisor;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostConstruct
    public void verificarConfiguracion() {
        log.info("Configuración SMTP inicializada. Correo emisor: {}, JavaMailSender disponible: {}",
                correoEmisor, mailSender != null);
    }

    @Override
    public void enviarOtp(String email, String codigo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();

        mensaje.setFrom(correoEmisor);
        mensaje.setTo(email);
        mensaje.setSubject("Código de verificación - StyleRadar");
        mensaje.setText(
                "Hola,\n\n" +
                        "Tu código de verificación para iniciar sesión en StyleRadar es: " +
                        codigo + "\n\n" +
                        "Este código tiene una validez de 5 minutos.\n" +
                        "No compartas este código con otras personas.\n\n" +
                        "Equipo StyleRadar"
        );

        mailSender.send(mensaje);
        log.info("Correo con código OTP enviado exitosamente a {}", email);
    }
}
