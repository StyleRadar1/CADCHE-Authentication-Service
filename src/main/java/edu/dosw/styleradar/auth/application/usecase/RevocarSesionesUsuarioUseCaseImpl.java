package edu.dosw.styleradar.auth.application.usecase;

import java.time.Clock;
import java.time.Instant;

import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;
import edu.dosw.styleradar.auth.domain.port.in.RevocarSesionesUsuarioUseCase;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import lombok.extern.slf4j.Slf4j;

/**
 * Revocación global de sesiones al inactivar o bloquear una cuenta. Idempotente por {@code eventoId}:
 * un mensaje duplicado de RabbitMQ no vuelve a procesarse.
 */
@Slf4j
public class RevocarSesionesUsuarioUseCaseImpl implements RevocarSesionesUsuarioUseCase {

    private final OtpService otpService;
    private final Clock clock;

    public RevocarSesionesUsuarioUseCaseImpl(OtpService otpService, Clock clock) {
        this.otpService = otpService;
        this.clock = clock;
    }

    @Override
    public void revocar(String eventoId, Long usuarioId, EstadoCuenta estadoNuevo, Instant fechaCambio) {
        if (usuarioId == null || estadoNuevo == null) {
            log.warn("Evento UsuarioEstadoCambiadoEvent incompleto o nulo ignorado");
            return;
        }
        if (!estadoNuevo.requiereRevocarSesiones()) {
            log.info("Transición a estado {} para usuario ID {}; no requiere revocación global", estadoNuevo, usuarioId);
            return;
        }
        if (eventoId != null && !eventoId.isBlank()
                && !otpService.registrarEventoProcesado(SecuritySanitizer.sanitizeForLog(eventoId))) {
            log.info("Evento duplicado {} descartado por idempotencia para usuario ID {}",
                    SecuritySanitizer.sanitizeForLog(eventoId), usuarioId);
            return;
        }

        otpService.revocarSesionesUsuario(usuarioId, fechaCambio != null ? fechaCambio : Instant.now(clock));
        log.warn("Sesiones globales revocadas para usuario ID {} por cambio a estado {}", usuarioId, estadoNuevo);
    }
}
