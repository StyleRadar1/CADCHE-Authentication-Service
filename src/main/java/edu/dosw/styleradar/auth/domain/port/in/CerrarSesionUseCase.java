package edu.dosw.styleradar.auth.domain.port.in;

/**
 * Cierre de sesión: revoca el token hasta su vencimiento.
 */
public interface CerrarSesionUseCase {

    void cerrarSesion(String token);
}
