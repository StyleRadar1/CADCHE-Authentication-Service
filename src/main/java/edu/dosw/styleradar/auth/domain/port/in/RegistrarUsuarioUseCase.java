package edu.dosw.styleradar.auth.domain.port.in;

import edu.dosw.styleradar.auth.domain.model.UsuarioRegistrado;

/**
 * Registro de soporte para pruebas locales. En QA y PROD responde fail-closed: el registro es de ms-usuarios.
 */
public interface RegistrarUsuarioUseCase {

    UsuarioRegistrado registrar(String nombre, String email, String telefono);
}
