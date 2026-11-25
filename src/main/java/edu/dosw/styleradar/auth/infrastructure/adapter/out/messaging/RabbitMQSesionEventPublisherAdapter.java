package edu.dosw.styleradar.auth.infrastructure.adapter.out.messaging;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import edu.dosw.styleradar.auth.domain.event.SesionCambiadaEvent;
import edu.dosw.styleradar.auth.domain.port.out.EventoSesionPublisherPort;
import edu.dosw.styleradar.auth.infrastructure.config.RabbitMQConfig;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Publica {@code user.session.changed} en {@code styleradar.exchange}. El envío va en un hilo aparte para que un
 * broker lento o caído nunca demore el login ni el logout; sin {@code RABBITMQ_PASSWORD} no se intenta enviar.
 */
@Component
@Slf4j
public class RabbitMQSesionEventPublisherAdapter implements EventoSesionPublisherPort {

    public static final String ROUTING_KEY = "user.session.changed";

    private final RabbitTemplate rabbitTemplate;
    private final boolean brokerConfigurado;
    private final ExecutorService envios = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(1000), r -> {
                Thread hilo = new Thread(r, "eventos-sesion");
                hilo.setDaemon(true);
                return hilo;
            });

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Bean de Spring inyectado por constructor.")
    public RabbitMQSesionEventPublisherAdapter(RabbitTemplate rabbitTemplate,
            @Value("${spring.rabbitmq.password:}") String password) {
        this.rabbitTemplate = rabbitTemplate;
        this.brokerConfigurado = password != null && !password.isBlank();
    }

    @Override
    public void publicarSesionCambiada(SesionCambiadaEvent evento) {
        if (!brokerConfigurado) {
            log.debug("user.session.changed no se publica: falta RABBITMQ_PASSWORD");
            return;
        }
        try {
            envios.execute(() -> enviar(evento));
        } catch (RejectedExecutionException ex) {
            log.warn("Cola de eventos de sesión llena; se descarta user.session.changed {}", evento.eventoId());
        }
    }

    void enviar(SesionCambiadaEvent evento) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, ROUTING_KEY, cuerpo(evento));
            log.info("Evento user.session.changed publicado: usuario {} {} ({})", evento.usuarioId(), evento.accion(),
                    evento.delta());
        } catch (AmqpException ex) {
            log.warn("No fue posible entregar user.session.changed a RabbitMQ: {}", ex.getMessage());
        }
    }

    /** Payload of user.session.changed: delta +1 on login, -1 on logout, expiration or revocation. */
    public static Map<String, Object> cuerpo(SesionCambiadaEvent evento) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("eventId", evento.eventoId());
        m.put("eventType", "USER_SESSION_CHANGED");
        m.put("userId", evento.usuarioId());
        m.put("sessionId", evento.sesionId());
        m.put("action", evento.accion().name());
        m.put("delta", evento.delta());
        m.put("occurredAt", fechaUtc(evento.fecha()));
        return m;
    }

    private static String fechaUtc(Instant fecha) {
        return fecha == null ? null : DateTimeFormatter.ISO_INSTANT.format(fecha.truncatedTo(ChronoUnit.SECONDS));
    }

    @PreDestroy
    void cerrar() {
        envios.shutdown();
    }
}
