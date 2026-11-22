package edu.dosw.styleradar.auth.domain.model;

import java.util.Locale;

/**
 * Estados de una cuenta de StyleRadar. Solo {@link #ACTIVA} puede iniciar sesión (RN-03).
 */
public enum EstadoCuenta {
    ACTIVA,
    INACTIVA,
    BLOQUEADA;

    /**
     * Acepta las variantes que publica ms-usuarios (masculino/femenino, mayúsculas o minúsculas).
     */
    public static EstadoCuenta fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVA", "ACTIVO", "ACTIVE" -> ACTIVA;
            case "INACTIVA", "INACTIVO", "INACTIVE" -> INACTIVA;
            case "BLOQUEADA", "BLOQUEADO", "BLOCKED" -> BLOQUEADA;
            default -> EstadoCuenta.valueOf(normalized);
        };
    }

    public boolean requiereRevocarSesiones() {
        return this == INACTIVA || this == BLOQUEADA;
    }
}
