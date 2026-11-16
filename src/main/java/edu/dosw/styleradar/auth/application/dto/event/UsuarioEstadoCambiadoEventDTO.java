package edu.dosw.styleradar.auth.application.dto.event;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;

/**
 * Mensaje {@code UsuarioEstadoCambiadoEvent} publicado por ms-usuarios en {@code styleradar.exchange}
 * (routing key {@code usuario.estado.cambiado}). Es un DTO de mensajería: el dominio no conoce Jackson.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UsuarioEstadoCambiadoEventDTO(
        String eventoId,
        Long usuarioId,
        EstadoCuenta estadoAnterior,
        EstadoCuenta estadoNuevo,
        Long administradorId,
        String motivo,
        Instant fechaCambio) {

    @JsonCreator
    public static UsuarioEstadoCambiadoEventDTO desdeJson(
            @JsonProperty("eventoId") String eventoId,
            @JsonProperty("usuarioId") Long usuarioId,
            @JsonProperty("estadoAnterior") String estadoAnterior,
            @JsonProperty("estadoNuevo") @JsonAlias("nuevoEstado") String estadoNuevo,
            @JsonProperty("administradorId") Long administradorId,
            @JsonProperty("motivo") String motivo,
            @JsonProperty("fechaCambio") Instant fechaCambio) {
        return new UsuarioEstadoCambiadoEventDTO(
                eventoId,
                usuarioId,
                EstadoCuenta.fromValue(estadoAnterior),
                EstadoCuenta.fromValue(estadoNuevo),
                administradorId,
                motivo,
                fechaCambio);
    }
}
