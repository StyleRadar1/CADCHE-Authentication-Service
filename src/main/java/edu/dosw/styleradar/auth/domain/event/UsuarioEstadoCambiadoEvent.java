package edu.dosw.styleradar.auth.domain.event;

import java.time.Instant;

import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;

public record UsuarioEstadoCambiadoEvent(
        Long usuarioId,
        EstadoCuenta estadoAnterior,
        EstadoCuenta estadoNuevo,
        Long administradorId,
        String motivo,
        Instant fechaCambio
) {
}