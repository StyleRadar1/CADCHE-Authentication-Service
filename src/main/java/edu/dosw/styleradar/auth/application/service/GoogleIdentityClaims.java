package edu.dosw.styleradar.auth.application.service;

/**
 * Claims verificados de identidad retornados por Google OpenID Connect.
 */
public record GoogleIdentityClaims(
        String subject,
        String email,
        boolean emailVerified,
        String name) {
}

