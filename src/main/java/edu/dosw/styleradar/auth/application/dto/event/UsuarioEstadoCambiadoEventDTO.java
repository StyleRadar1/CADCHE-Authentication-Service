package edu.dosw.styleradar.auth.application.dto.event;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;

/**
 * Event {@code user.status.changed} published by ms-usuarios on the {@code styleradar.users} exchange
 * (ms-usuarios requirements analysis v2, SR-RF-66): userId, previousStatus, newStatus, changedBy, reason, changedAt
 * and eventId. The Spanish names of the previous version are still accepted while ms-usuarios migrates.
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
            @JsonProperty("eventId") @JsonAlias("eventoId") String eventoId,
            @JsonProperty("userId") @JsonAlias("usuarioId") Long usuarioId,
            @JsonProperty("previousStatus") @JsonAlias("estadoAnterior") String estadoAnterior,
            @JsonProperty("newStatus") @JsonAlias({"estadoNuevo", "nuevoEstado"}) String estadoNuevo,
            @JsonProperty("changedBy") @JsonAlias("administradorId") Long administradorId,
            @JsonProperty("reason") @JsonAlias("motivo") String motivo,
            @JsonProperty("changedAt") @JsonAlias("fechaCambio") Instant fechaCambio) {
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
