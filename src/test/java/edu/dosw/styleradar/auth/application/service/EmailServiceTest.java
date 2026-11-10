package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import edu.dosw.styleradar.auth.application.service.impl.EmailServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias de EmailService - SR-RF-92")
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailServiceImpl(mailSender);
        ReflectionTestUtils.setField(emailService, "correoEmisor", "styleradar1@gmail.com");
    }

    @Test
    @DisplayName("Debe construir y enviar el mensaje SimpleMailMessage con destinatario, asunto y código de 6 dígitos")
    void debeEnviarCorreoConCodigoOtp() {
        String destino = "cliente@styleradar.com";
        String codigo = "654321";

        emailService.enviarOtp(destino, codigo);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());

        SimpleMailMessage mensaje = captor.getValue();
        assertThat(mensaje.getTo()).containsExactly(destino);
        assertThat(mensaje.getFrom()).isEqualTo("styleradar1@gmail.com");
        assertThat(mensaje.getSubject()).isEqualTo("Código de verificación - StyleRadar");
        assertThat(mensaje.getText())
                .contains("654321")
                .contains("5 minutos")
                .contains("Equipo StyleRadar");
    }
}

