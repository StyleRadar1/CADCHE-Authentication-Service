package edu.dosw.styleradar.auth.domain.event;

import java.time.Instant;
import java.util.UUID;

import edu.dosw.styleradar.auth.domain.model.SesionActiva;

/**
 * Cambio en el número de sesiones activas (evento {@code user.session.changed}): +1 al iniciar sesión y -1 al
 * cerrarla, al vencer el token o al revocarse por un cambio de estado de la cuenta.
 */
public record SesionCambiadaEvent(String eventoId, Long usuarioId, String sesionId, Accion accion, int delta,
        Instant fecha) {

    /** Motivo del cambio de sesión. */
    public enum Accion {
        LOGIN(1), LOGOUT(-1), EXPIRED(-1), REVOKED(-1);

        private final int delta;

        Accion(int delta) {
            this.delta = delta;
        }

        public int delta() {
            return delta;
        }
    }

    public static SesionCambiadaEvent de(SesionActiva sesion, Accion accion, Instant fecha) {
        return new SesionCambiadaEvent(UUID.randomUUID().toString(), sesion.usuarioId(), sesion.sesionId(), accion,
                accion.delta(), fecha);
    }
}
