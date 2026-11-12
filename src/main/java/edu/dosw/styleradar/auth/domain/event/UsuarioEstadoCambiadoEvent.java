package edu.dosw.styleradar.auth.domain.event;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioEstadoCambiadoEvent(
        String eventoId,
        Long usuarioId,
        EstadoCuenta estadoAnterior,
        @JsonAlias("nuevoEstado") EstadoCuenta estadoNuevo,
        Long administradorId,
        String motivo,
        Instant fechaCambio
) {
    public UsuarioEstadoCambiadoEvent(
            Long usuarioId,
            EstadoCuenta estadoAnterior,
            EstadoCuenta estadoNuevo,
            Long administradorId,
            String motivo,
            Instant fechaCambio) {
        this(null, usuarioId, estadoAnterior, estadoNuevo, administradorId, motivo, fechaCambio);
    }
}