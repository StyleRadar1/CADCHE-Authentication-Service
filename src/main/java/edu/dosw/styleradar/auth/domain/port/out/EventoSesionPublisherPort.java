package edu.dosw.styleradar.auth.domain.port.out;

import edu.dosw.styleradar.auth.domain.event.SesionCambiadaEvent;

/** Publicación de user.session.changed para ms-estadísticas. */
public interface EventoSesionPublisherPort {

    void publicarSesionCambiada(SesionCambiadaEvent evento);
}
