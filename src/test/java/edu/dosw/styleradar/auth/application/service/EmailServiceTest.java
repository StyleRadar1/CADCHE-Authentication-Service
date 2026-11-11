package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import edu.dosw.styleradar.auth.application.service.impl.EmailServiceImpl;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias de EmailService - SR-RF-92 / OWASP A10:2025")
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

    @Test
    @DisplayName("Debe fallar cerrado con 503 cuando SMTP falla fuera del perfil dev (OWASP A10:2025)")
    void debeFallarCerradoCuandoSmtpFallaSinPerfilDev() {
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> emailService.enviarOtp("cliente@styleradar.com", "123456"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasMessageContaining("correo electrónico");
    }

    @Test
    @DisplayName("Debe fallar cerrado aunque devConsoleFallbackEnabled sea true si prod está activo")
    void debeFallarCerradoEnProdInclusoConFlagDevActivo() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("dev", "prod");
        EmailServiceImpl prodService = new EmailServiceImpl(mailSender, true, prodEnv);
        ReflectionTestUtils.setField(prodService, "correoEmisor", "styleradar1@gmail.com");

        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> prodService.enviarOtp("cliente@styleradar.com", "123456"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    @DisplayName("Debe permitir respaldo en consola únicamente en perfil dev puro")
    void debePermitirFallbackSoloEnPerfilDevPuro() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        EmailServiceImpl devService = new EmailServiceImpl(mailSender, true, devEnv);
        ReflectionTestUtils.setField(devService, "correoEmisor", "styleradar1@gmail.com");

        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatCode(() -> devService.enviarOtp("cliente@styleradar.com", "123456"))
                .doesNotThrowAnyException();
    }
}
