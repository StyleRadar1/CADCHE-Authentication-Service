package edu.dosw.styleradar.auth.domain.port.in;

import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;

/**
 * Renueva un token vigente con rotación: el token anterior queda revocado.
 */
public interface RenovarTokenUseCase {

    SesionAutenticada renovar(String token);
}
