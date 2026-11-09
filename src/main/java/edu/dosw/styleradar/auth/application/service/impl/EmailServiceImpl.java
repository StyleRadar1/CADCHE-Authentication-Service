package edu.dosw.styleradar.auth.application.service.impl;

import edu.dosw.styleradar.auth.application.service.EmailService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String correoEmisor;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @PostConstruct
    public void verificarConfiguracion() {
        System.out.println("========== CONFIGURACION SMTP ==========");
        System.out.println("Correo emisor: " + correoEmisor);
        System.out.println("JavaMailSender disponible: " + (mailSender != null));
        System.out.println("========================================");
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
    }
}

