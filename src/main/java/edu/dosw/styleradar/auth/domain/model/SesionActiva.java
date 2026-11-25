package edu.dosw.styleradar.auth.domain.model;

import java.time.Instant;

/**
 * Sesión abierta en ms-auth. {@code sesionId} se mantiene al renovar el token; {@code tokenId} es el
 * {@code jti} del token vigente y {@code expira} su vencimiento.
 */
public record SesionActiva(String sesionId, Long usuarioId, String tokenId, Instant expira) {
}
