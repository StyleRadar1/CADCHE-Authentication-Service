package edu.dosw.styleradar.auth.domain.model;

import java.util.Locale;

import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;

/**
 * Roles RBAC de StyleRadar (contrato v1.2, sección 1.3).
 */
public enum Rol {
    COMPRADOR,
    ADMIN_ALMACEN,
    REPRESENTANTE_FUNDACION,
    ADMIN_STYLERADAR;

    /**
     * Convierte el rol recibido de ms-usuarios. Un rol ausente o desconocido nunca habilita sesión (RN-02).
     */
    public static Rol desde(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new CredencialesIncorrectasException("Rol de usuario no válido en la respuesta de identidad");
        }
        try {
            return Rol.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new CredencialesIncorrectasException("Rol de usuario no válido en la respuesta de identidad");
        }
    }
}
