package edu.dosw.styleradar.auth.domain.model;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum EstadoCuenta {
    ACTIVA,
    INACTIVA,
    BLOQUEADA;

    @JsonCreator
    public static EstadoCuenta fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ACTIVA", "ACTIVO" -> ACTIVA;
            case "INACTIVA", "INACTIVO" -> INACTIVA;
            case "BLOQUEADA", "BLOQUEADO" -> BLOQUEADA;
            default -> EstadoCuenta.valueOf(normalized);
        };
    }
}