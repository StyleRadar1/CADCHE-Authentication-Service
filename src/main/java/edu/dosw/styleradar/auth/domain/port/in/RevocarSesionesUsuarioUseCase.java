package edu.dosw.styleradar.auth.domain.port.in;

import java.time.Instant;

import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;

/**
 * Revoca todas las sesiones de un usuario cuando ms-usuarios lo inactiva o bloquea
 * (evento {@code UsuarioEstadoCambiadoEvent}, consumo idempotente por {@code eventoId}).
 */
public interface RevocarSesionesUsuarioUseCase {

    void revocar(String eventoId, Long usuarioId, EstadoCuenta estadoNuevo, Instant fechaCambio);
}
