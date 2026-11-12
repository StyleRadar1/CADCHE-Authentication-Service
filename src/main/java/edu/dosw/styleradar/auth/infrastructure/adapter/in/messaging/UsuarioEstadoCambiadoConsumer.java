package edu.dosw.styleradar.auth.infrastructure.adapter.in.messaging;

import java.time.Duration;
import java.time.Instant;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import edu.dosw.styleradar.auth.domain.event.UsuarioEstadoCambiadoEvent;
import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;
import edu.dosw.styleradar.auth.infrastructure.config.RabbitMQConfig;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class UsuarioEstadoCambiadoConsumer {

    private static final Duration EVENTO_IDEMPOTENCIA_TTL = Duration.ofHours(24);
    private final StringRedisTemplate redisTemplate;

    public UsuarioEstadoCambiadoConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void recibir(UsuarioEstadoCambiadoEvent evento) {
        if (evento == null || evento.usuarioId() == null || evento.estadoNuevo() == null) {
            log.warn("Evento UsuarioEstadoCambiadoEvent incompleto o nulo ignorado");
            return;
        }

        if (evento.estadoNuevo() != EstadoCuenta.BLOQUEADA && evento.estadoNuevo() != EstadoCuenta.INACTIVA) {
            log.info("Transición a estado {} recibida para usuario ID {}; no requiere nueva revocación global",
                    evento.estadoNuevo(), evento.usuarioId());
            return;
        }

        Instant fechaCambio = evento.fechaCambio() != null ? evento.fechaCambio() : Instant.now();
        String clave = "usuario:revocado:" + evento.usuarioId();

        try {
            ValueOperations<String, String> ops = redisTemplate.opsForValue();
            if (ops != null) {
                if (evento.eventoId() != null && !evento.eventoId().isBlank()) {
                    String claveEvento = "evento:procesado:" + SecuritySanitizer.sanitizeForLog(evento.eventoId());
                    Boolean primeraVez = ops.setIfAbsent(claveEvento, "1", EVENTO_IDEMPOTENCIA_TTL);
                    if (Boolean.FALSE.equals(primeraVez)) {
                        log.info("Evento duplicado {} descartado por idempotencia para usuario ID {}",
                                claveEvento, evento.usuarioId());
                        return;
                    }
                }
                ops.set(clave, String.valueOf(fechaCambio.toEpochMilli()));
            }
            log.warn("Sesiones globales revocadas para usuario ID {} por cambio a estado {}",
                    evento.usuarioId(), evento.estadoNuevo());
        } catch (DataAccessException ex) {
            log.error("Error persistiendo revocación global en Redis para usuario ID {}: {}",
                    evento.usuarioId(), SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            throw ex;
        }
    }
}
