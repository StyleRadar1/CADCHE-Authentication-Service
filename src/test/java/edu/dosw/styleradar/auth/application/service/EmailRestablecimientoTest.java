package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import edu.dosw.styleradar.auth.application.service.impl.EmailServiceImpl;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;

@DisplayName("EmailService: correos de restablecimiento de contraseña")
class EmailRestablecimientoTest {

    private EmailServiceImpl servicio(JavaMailSender sender, boolean consolaDev, String... perfiles) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(perfiles);
        EmailServiceImpl s = new EmailServiceImpl(sender, consolaDev, env);
        ReflectionTestUtils.setField(s, "correoEmisor", "styleradar1@gmail.com");
        return s;
    }

    private SimpleMailMessage enviado(JavaMailSender sender) {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        return captor.getValue();
    }

    @Test
    void codigoDeRestablecimientoTieneSuPropioAsuntoYAdvertencia() {
        JavaMailSender sender = mock(JavaMailSender.class);
        servicio(sender, false, "qa").enviarCodigoRestablecimiento("Tienda@AlmacenAliado.co", "482915");

        SimpleMailMessage m = enviado(sender);
        assertThat(m.getTo()).containsExactly("tienda@almacenaliado.co");
        assertThat(m.getSubject()).isEqualTo("Restablecer tu contraseña - StyleRadar");
        assertThat(m.getText()).contains("482915", "5 minutos", "Si no fuiste tú");
    }

    @Test
    void avisoDeCambioNoIncluyeCodigos() {
        JavaMailSender sender = mock(JavaMailSender.class);
        servicio(sender, false, "prod").enviarAvisoContrasenaCambiada("buyer@styleradar.com");

        SimpleMailMessage m = enviado(sender);
        assertThat(m.getSubject()).isEqualTo("Tu contraseña cambió - StyleRadar");
        assertThat(m.getText()).contains("cerramos todas tus sesiones").doesNotContainPattern("\\d{6}");
    }

    @Test
    void sinSmtpFallaCerradoFueraDeDevYUsaConsolaSoloEnDev() {
        JavaMailSender caido = mock(JavaMailSender.class);
        doThrow(new MailSendException("SMTP caído")).when(caido).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> servicio(caido, true, "qa").enviarAvisoContrasenaCambiada("buyer@styleradar.com"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
        servicio(caido, true, "dev").enviarCodigoRestablecimiento("buyer@styleradar.com", "482915");
        servicio(caido, true, "dev").enviarAvisoContrasenaCambiada("buyer@styleradar.com");
        assertThatThrownBy(() -> servicio(null, false, "qa").enviarCodigoRestablecimiento("buyer@styleradar.com", "1"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }
}
