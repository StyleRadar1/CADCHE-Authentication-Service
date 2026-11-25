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
 * un mensaje duplicado de RabbitMQ no vuelve a procesarse, y un fallo al revocar nunca deja el evento
 * marcado como procesado (el reintento sí revoca).
 */
@Slf4j
public class RevocarSesionesUsuarioUseCaseImpl implements RevocarSesionesUsuarioUseCase {

    private final OtpService otpService;
    private final Clock clock;
    private final edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones;

    public RevocarSesionesUsuarioUseCaseImpl(OtpService otpService, Clock clock) {
        this(otpService, clock, null);
    }

    public RevocarSesionesUsuarioUseCaseImpl(OtpService otpService, Clock clock, edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones) {
        this.sesiones = sesiones;
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
        boolean conId = eventoId != null && !eventoId.isBlank();
        String idSeguro = conId ? SecuritySanitizer.sanitizeForLog(eventoId) : null;
        if (conId && otpService.eventoYaProcesado(idSeguro)) {
            log.info("Evento duplicado {} descartado por idempotencia para usuario ID {}", idSeguro, usuarioId);
            return;
        }

        // Primero la revocación y después la marca de idempotencia: si Redis falla al revocar, la excepción
        // provoca el reintento de RabbitMQ y el evento no queda marcado como procesado (CADCHE-129).
        otpService.revocarSesionesUsuario(usuarioId, fechaCambio != null ? fechaCambio : Instant.now(clock));
        if (sesiones != null) {
            sesiones.revocarUsuario(usuarioId);
        }
        if (conId) {
            otpService.registrarEventoProcesado(idSeguro);
        }
        log.warn("Sesiones globales revocadas para usuario ID {} por cambio a estado {}", usuarioId, estadoNuevo);
    }
}
