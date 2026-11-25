package edu.dosw.styleradar.auth.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import edu.dosw.styleradar.auth.domain.model.SesionActiva;

/**
 * Registro de sesiones abiertas. {@code finalizar} es atómico: si dos instancias intentan cerrar la misma sesión,
 * solo una la recibe, así cada sesión genera un único -1.
 */
public interface RegistroSesionesPort {

    void registrar(SesionActiva sesion);

    /** Quita la sesión del token indicado y la devuelve, o vacío si ya no estaba registrada. */
    Optional<SesionActiva> finalizar(String tokenId);

    /** Pasa la sesión del token anterior al token renovado sin cerrarla; vacío si el anterior no estaba registrado. */
    Optional<SesionActiva> reemplazar(String tokenIdAnterior, String tokenIdNuevo, Instant expiraNueva);

    /** Quita y devuelve hasta {@code limite} sesiones cuyo token venció antes de {@code ahora}. */
    List<SesionActiva> finalizarVencidas(Instant ahora, int limite);

    /** Quita y devuelve todas las sesiones abiertas del usuario. */
    List<SesionActiva> finalizarDeUsuario(Long usuarioId);
}
