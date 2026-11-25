package edu.dosw.styleradar.auth.domain.port.in;

import java.time.Instant;

import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;

/**
 * Revoca todas las sesiones de un usuario cuando ms-usuarios lo inactiva o bloquea
 * (evento {@code UsuarioEstadoCambiadoEvent}, consumo idempotente por {@code eventoId}).
 */
public interface RevocarSesionesUsuarioUseCase {

    void revocar(String eventoId, Long usuarioId, EstadoCuenta estadoNuevo, Instant fechaCambio);

    /**
     * Revoca todas las sesiones porque la cuenta cambió de contraseña ({@code user.password.changed}) o fue
     * eliminada en ms-usuarios. Es idempotente por {@code eventoId} cuando el evento lo trae.
     */
    void revocarPorCambioDeCuenta(String eventoId, Long usuarioId, Instant fecha, String motivo);
}
