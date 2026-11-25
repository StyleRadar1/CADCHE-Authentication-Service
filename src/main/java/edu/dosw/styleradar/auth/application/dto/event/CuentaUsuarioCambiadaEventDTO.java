package edu.dosw.styleradar.auth.application.dto.event;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Events from ms-usuarios that invalidate every session of an account:
 * <ul>
 *   <li>{@code user.password.changed}: {@code eventId}, {@code eventType}, {@code userId}, {@code changedAt}.</li>
 *   <li>{@code styleradar.usuarios.cuenta.eliminada} (or {@code user.deleted}): {@code userId}, {@code deletedAt}.</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CuentaUsuarioCambiadaEventDTO(String eventoId, String tipo, Long usuarioId, Instant fecha) {

    @JsonCreator
    public static CuentaUsuarioCambiadaEventDTO desdeJson(
            @JsonProperty("eventId") @JsonAlias("eventoId") String eventoId,
            @JsonProperty("eventType") @JsonAlias("tipoEvento") String tipo,
            @JsonProperty("userId") @JsonAlias("usuarioId") Long usuarioId,
            @JsonProperty("changedAt") @JsonAlias({"fechaCambio", "fecha"}) Instant cambiadaEn,
            @JsonProperty("deletedAt") @JsonAlias("eliminadaEn") Instant eliminadaEn) {
        return new CuentaUsuarioCambiadaEventDTO(eventoId,
                tipo != null ? tipo : (eliminadaEn != null ? "user.deleted" : null),
                usuarioId, cambiadaEn != null ? cambiadaEn : eliminadaEn);
    }
}
