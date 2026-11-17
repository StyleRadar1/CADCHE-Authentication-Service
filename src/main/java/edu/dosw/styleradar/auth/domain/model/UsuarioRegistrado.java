package edu.dosw.styleradar.auth.domain.model;

import java.time.Instant;

/**
 * Usuario creado por el registro de soporte (solo dev/test; el registro real es de ms-usuarios).
 */
public record UsuarioRegistrado(
        Long id,
        String nombre,
        String email,
        String telefono,
        Instant fechaRegistro,
        Rol rol) {
}
