package edu.dosw.styleradar.auth.domain.model;

/**
 * Identidad verificada por ms-usuarios (dueño de las credenciales, Database-per-Service).
 */
public record IdentidadUsuario(
        Long id,
        String email,
        Rol rol,
        EstadoCuenta estado,
        boolean correoVerificado) {

    public boolean estaActiva() {
        return estado == EstadoCuenta.ACTIVA;
    }
}
