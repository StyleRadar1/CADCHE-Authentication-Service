package edu.dosw.styleradar.auth.infrastructure.adapter.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import edu.dosw.styleradar.auth.domain.event.SesionCambiadaEvent;
import edu.dosw.styleradar.auth.domain.event.SesionCambiadaEvent.Accion;
import edu.dosw.styleradar.auth.domain.model.SesionActiva;
import edu.dosw.styleradar.auth.infrastructure.config.RabbitMQConfig;

@DisplayName("Publicación de user.session.changed")
class RabbitMQSesionEventPublisherAdapterTest {

    private static final Instant FECHA = Instant.parse("2026-11-25T15:04:05.321Z");
    private final SesionCambiadaEvent login = SesionCambiadaEvent.de(
            new SesionActiva("sesion-1", 12L, "jti-1", FECHA.plusSeconds(43200)), Accion.LOGIN, FECHA);

    @Test
    @DisplayName("Payload con eventId, eventType, userId, sessionId, action, delta y occurredAt en UTC")
    void payload() {
        assertThat(RabbitMQSesionEventPublisherAdapter.cuerpo(login))
                .containsOnlyKeys("eventId", "eventType", "userId", "sessionId", "action", "delta", "occurredAt")
                .containsEntry("eventType", "USER_SESSION_CHANGED").containsEntry("userId", 12L)
                .containsEntry("sessionId", "sesion-1").containsEntry("action", "LOGIN").containsEntry("delta", 1)
                .containsEntry("occurredAt", "2026-11-25T15:04:05Z");
        SesionCambiadaEvent logout = SesionCambiadaEvent.de(
                new SesionActiva("sesion-1", 12L, "jti-1", null), Accion.LOGOUT, null);
        assertThat(RabbitMQSesionEventPublisherAdapter.cuerpo(logout))
                .containsEntry("action", "LOGOUT").containsEntry("delta", -1).containsEntry("occurredAt", null);
    }

    @Test
    @DisplayName("Publica en styleradar.exchange con la routing key user.session.changed, fuera del hilo de la petición")
    void publica() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        RabbitMQSesionEventPublisherAdapter adapter = new RabbitMQSesionEventPublisherAdapter(template, "clave");

        adapter.publicarSesionCambiada(login);

        verify(template, timeout(3000)).convertAndSend(eq(RabbitMQConfig.EXCHANGE), eq("user.session.changed"),
                any(Object.class));
        adapter.cerrar();
    }

    @Test
    @DisplayName("Broker caído no lanza error y sin RABBITMQ_PASSWORD no intenta publicar")
    void tolerante() {
        RabbitTemplate template = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new RuntimeException("broker caído"))).when(template)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));
        RabbitMQSesionEventPublisherAdapter adapter = new RabbitMQSesionEventPublisherAdapter(template, "clave");
        assertThatCode(() -> adapter.enviar(login)).doesNotThrowAnyException();

        RabbitTemplate sinUso = mock(RabbitTemplate.class);
        RabbitMQSesionEventPublisherAdapter sinClave = new RabbitMQSesionEventPublisherAdapter(sinUso, " ");
        sinClave.publicarSesionCambiada(login);
        new RabbitMQSesionEventPublisherAdapter(sinUso, null).publicarSesionCambiada(login);
        verifyNoInteractions(sinUso);
    }
}
