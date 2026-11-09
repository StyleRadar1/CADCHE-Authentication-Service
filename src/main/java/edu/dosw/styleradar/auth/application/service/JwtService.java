package edu.dosw.styleradar.auth.application.service;

import java.util.Set;

import edu.dosw.styleradar.auth.domain.model.Rol;

public interface JwtService {

    String generarToken(Long usuarioId, Set<Rol> roles);
}