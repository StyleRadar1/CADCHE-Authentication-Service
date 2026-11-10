package edu.dosw.styleradar.auth.infrastructure.adapter.in.messaging;

import java.time.Instant;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import edu.dosw.styleradar.auth.domain.event.UsuarioEstadoCambiadoEvent;

@Component
public class UsuarioEstadoCambiadoConsumer {

    private final StringRedisTemplate redisTemplate;

    public UsuarioEstadoCambiadoConsumer(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @RabbitListener(queues = "auth.usuario.estado.cambiado")
    public void recibir(UsuarioEstadoCambiadoEvent evento) {

        String clave = "usuario:revocado:" + evento.usuarioId();

        redisTemplate.opsForValue().set(
                clave,
                String.valueOf(evento.fechaCambio().toEpochMilli())
        );
    }
}