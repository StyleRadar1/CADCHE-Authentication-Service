package edu.dosw.styleradar.auth.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import edu.dosw.styleradar.auth.domain.event.SesionCambiadaEvent;
import edu.dosw.styleradar.auth.domain.event.SesionCambiadaEvent.Accion;
import edu.dosw.styleradar.auth.domain.model.SesionActiva;
import edu.dosw.styleradar.auth.domain.port.out.EventoSesionPublisherPort;
import edu.dosw.styleradar.auth.domain.port.out.RegistroSesionesPort;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;

/**
 * Lleva el registro de sesiones abiertas y publica {@code user.session.changed}. Es un dato estadístico: si Redis o
 * el broker fallan, se registra en el log y el inicio, la renovación o el cierre de sesión continúan normalmente.
 * Sin registro o sin publicador (por ejemplo, en pruebas de un solo controlador) queda inactivo.
 */
@Slf4j
public class SesionesActivasService {

    /** Máximo de sesiones vencidas que se procesan en cada barrido. */
    public static final int LOTE_VENCIDAS = 500;

    private final JwtService jwtService;
    private final RegistroSesionesPort registro;
    private final EventoSesionPublisherPort eventos;
    private final Clock clock;
    private final boolean activo;

    public SesionesActivasService(JwtService jwtService, RegistroSesionesPort registro,
            EventoSesionPublisherPort eventos, Clock clock) {
        this.jwtService = jwtService;
        this.registro = registro;
        this.eventos = eventos;
        this.clock = clock;
        this.activo = registro != null && eventos != null;
    }

    /** Sesión nueva (login directo, OTP verificado o Google): +1. */
    public void iniciar(String token) {
        if (!activo) {
            return;
        }
        seguro("registrar el inicio de sesión", () -> {
            Claims claims = jwtService.validarToken(token);
            SesionActiva sesion = new SesionActiva(UUID.randomUUID().toString(), usuarioId(claims), claims.getId(),
                    claims.getExpiration().toInstant());
            registro.registrar(sesion);
            publicar(sesion, Accion.LOGIN);
        });
    }

    /** Renovación con rotación: la sesión sigue abierta con el token nuevo y no se publica nada. */
    public void renovar(String tokenAnterior, String tokenNuevo) {
        if (!activo) {
            return;
        }
        seguro("trasladar la sesión al token renovado", () -> {
            Claims anterior = jwtService.validarToken(tokenAnterior);
            Claims nuevo = jwtService.validarToken(tokenNuevo);
            registro.reemplazar(anterior.getId(), nuevo.getId(), nuevo.getExpiration().toInstant());
        });
    }

    /** Cierre de sesión: -1 una sola vez aunque el cliente repita el logout. */
    public void cerrar(String token) {
        if (!activo) {
            return;
        }
        seguro("registrar el cierre de sesión", () -> {
            Claims claims = jwtService.validarToken(token);
            registro.finalizar(claims.getId()).ifPresent(s -> publicar(s, Accion.LOGOUT));
        });
    }

    /** Cuenta inactivada o bloqueada en ms-usuarios: -1 por cada sesión abierta del usuario. */
    public void revocarUsuario(Long usuarioId) {
        if (!activo) {
            return;
        }
        seguro("registrar la revocación de sesiones", () ->
                registro.finalizarDeUsuario(usuarioId).forEach(s -> publicar(s, Accion.REVOKED)));
    }

    /** Barrido de tokens vencidos: -1 por cada sesión cuyo token expiró. Devuelve cuántas cerró. */
    public int expirarVencidas(Instant ahora) {
        if (!activo) {
            return 0;
        }
        try {
            List<SesionActiva> vencidas = registro.finalizarVencidas(ahora, LOTE_VENCIDAS);
            vencidas.forEach(s -> publicar(s, Accion.EXPIRED));
            return vencidas.size();
        } catch (RuntimeException ex) {
            log.warn("No fue posible revisar las sesiones vencidas: {}", ex.getMessage());
            return 0;
        }
    }

    private void publicar(SesionActiva sesion, Accion accion) {
        eventos.publicarSesionCambiada(SesionCambiadaEvent.de(sesion, accion, Instant.now(clock)));
    }

    private static Long usuarioId(Claims claims) {
        return claims.getSubject() != null ? Long.valueOf(claims.getSubject()) : null;
    }

    private static void seguro(String accion, Runnable paso) {
        try {
            paso.run();
        } catch (RuntimeException ex) {
            log.warn("No fue posible {} para las estadísticas de sesiones: {}", accion, ex.getMessage());
        }
    }
}
