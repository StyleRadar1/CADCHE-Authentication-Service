package edu.dosw.styleradar.auth.domain.model;

/**
 * Claims verificados de identidad retornados por Google OpenID Connect.
 */
public record GoogleIdentityClaims(
        String subject,
        String email,
        boolean emailVerified,
        String name) {
}

