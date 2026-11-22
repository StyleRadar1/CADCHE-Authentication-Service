package edu.dosw.styleradar.auth.domain.model;

import java.util.Locale;

import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;

/**
 * StyleRadar RBAC roles (requirements analysis v2: BUYER, STORE_ADMIN, FOUNDATION_REPRESENTATIVE, STYLERADAR_ADMIN).
 * <p>
 * ms-usuarios todavía puede enviar los nombres anteriores en español; {@link #desde(String)} los traduce para que el
 * JWT y las respuestas siempre salgan con los nombres en inglés.
 */
public enum Rol {
    BUYER,
    STORE_ADMIN,
    FOUNDATION_REPRESENTATIVE,
    STYLERADAR_ADMIN;

    /**
     * Convierte el rol recibido de ms-usuarios. Un rol ausente o desconocido nunca habilita sesión (RN-02).
     */
    public static Rol desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new CredencialesIncorrectasException("Rol de usuario no válido en la respuesta de identidad");
        }
        String normalizado = valor.trim().toUpperCase(Locale.ROOT);
        if (normalizado.startsWith("ROLE_")) {
            normalizado = normalizado.substring("ROLE_".length());
        }
        switch (normalizado) {
            case "COMPRADOR" -> normalizado = "BUYER";
            case "ADMIN_ALMACEN", "TIENDA" -> normalizado = "STORE_ADMIN";
            case "REPRESENTANTE_FUNDACION", "FUNDACION" -> normalizado = "FOUNDATION_REPRESENTATIVE";
            case "ADMIN_STYLERADAR", "ADMIN" -> normalizado = "STYLERADAR_ADMIN";
            default -> { }
        }
        try {
            return Rol.valueOf(normalizado);
        } catch (IllegalArgumentException ex) {
            throw new CredencialesIncorrectasException("Rol de usuario no válido en la respuesta de identidad");
        }
    }
}
