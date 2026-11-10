package edu.dosw.styleradar.auth.application.service;

import java.util.Set;

import edu.dosw.styleradar.auth.domain.model.Rol;

public interface JwtService {

    String generarToken(Long usuarioId, Set<Rol> roles);

    boolean validarToken(String token);

    Long obtenerUsuarioId(String token);

    Set<Rol> obtenerRoles(String token);

    long obtenerTiempoRestanteSegundos(String token);
}
