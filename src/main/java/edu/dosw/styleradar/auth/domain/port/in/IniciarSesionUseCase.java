package edu.dosw.styleradar.auth.domain.port.in;

import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;

/**
 * SR-RF-02 · Iniciar sesión con correo y contraseña.
 */
public interface IniciarSesionUseCase {

    SesionAutenticada iniciarSesion(String email, String password);
}
