package edu.dosw.styleradar.auth.infrastructure.adapter.in.amqp;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import edu.dosw.styleradar.auth.application.dto.event.CuentaUsuarioCambiadaEventDTO;
import edu.dosw.styleradar.auth.domain.port.in.RevocarSesionesUsuarioUseCase;
import edu.dosw.styleradar.auth.infrastructure.config.RabbitMQConfig;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

/**
 * Revoca todas las sesiones cuando ms-usuarios avisa que la cuenta cambió de contraseña o fue eliminada.
 */
@Component
@Slf4j
public class CuentaUsuarioCambiadaConsumer {

    private final RevocarSesionesUsuarioUseCase revocarSesionesUsuarioUseCase;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Caso de uso administrado por Spring e inyectado por constructor.")
    public CuentaUsuarioCambiadaConsumer(RevocarSesionesUsuarioUseCase revocarSesionesUsuarioUseCase) {
        this.revocarSesionesUsuarioUseCase = revocarSesionesUsuarioUseCase;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_CUENTA)
    public void recibir(CuentaUsuarioCambiadaEventDTO evento) {
        if (evento == null) {
            log.warn("Evento de cuenta de usuario nulo ignorado");
            return;
        }
        String motivo = evento.tipo() != null ? evento.tipo() : "cambio de cuenta";
        revocarSesionesUsuarioUseCase.revocarPorCambioDeCuenta(
                evento.eventoId(), evento.usuarioId(), evento.fecha(), motivo);
    }
}
