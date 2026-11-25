package edu.dosw.styleradar.auth.domain.port.out;

import java.util.Set;

import edu.dosw.styleradar.auth.domain.model.EntidadAsociada;
import edu.dosw.styleradar.auth.domain.model.Rol;

/**
 * Puerto de salida para consultar las entidades asociadas a un usuario (tienda o fundación)
 * al momento de emitir o renovar su JWT. Debe operar de forma tolerante a fallos (fail-open).
 */
public interface EntidadAsociadaPort {

    EntidadAsociada resolverEntidadAsociada(Long usuarioId, Set<Rol> roles);
}
