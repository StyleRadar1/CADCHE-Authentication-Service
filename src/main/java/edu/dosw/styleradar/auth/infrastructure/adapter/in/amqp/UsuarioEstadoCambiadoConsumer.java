package edu.dosw.styleradar.auth.infrastructure.adapter.in.amqp;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import edu.dosw.styleradar.auth.application.dto.event.UsuarioEstadoCambiadoEventDTO;
import edu.dosw.styleradar.auth.domain.port.in.RevocarSesionesUsuarioUseCase;
import edu.dosw.styleradar.auth.infrastructure.config.RabbitMQConfig;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador AMQP de entrada: consume {@code UsuarioEstadoCambiadoEvent} de ms-usuarios y delega en el
 * caso de uso de revocación. Un fallo se reintenta 3 veces y luego va a la DLQ {@code styleradar.auth.dlq}.
 */
@Component
@Slf4j
public class UsuarioEstadoCambiadoConsumer {

    private final RevocarSesionesUsuarioUseCase revocarSesionesUsuarioUseCase;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Caso de uso administrado por Spring e inyectado por constructor.")
    public UsuarioEstadoCambiadoConsumer(RevocarSesionesUsuarioUseCase revocarSesionesUsuarioUseCase) {
        this.revocarSesionesUsuarioUseCase = revocarSesionesUsuarioUseCase;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void recibir(UsuarioEstadoCambiadoEventDTO evento) {
        if (evento == null) {
            log.warn("Evento UsuarioEstadoCambiadoEvent nulo ignorado");
            return;
        }
        revocarSesionesUsuarioUseCase.revocar(
                evento.eventoId(), evento.usuarioId(), evento.estadoNuevo(), evento.fechaCambio());
    }
}
