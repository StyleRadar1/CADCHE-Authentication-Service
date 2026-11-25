package edu.dosw.styleradar.auth.application.usecase;

import java.util.Set;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;
import edu.dosw.styleradar.auth.domain.port.in.RenovarTokenUseCase;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;

/**
 * Renovación con rotación: emite un token nuevo y revoca el anterior hasta su vencimiento.
 * Rechaza tokens revocados por logout o por bloqueo global de la cuenta.
 */
@Slf4j
public class RenovarTokenUseCaseImpl implements RenovarTokenUseCase {

    private static final String TOKEN_NO_VALIDO = "El token presentado es inválido, expiró o fue revocado";

    private final JwtService jwtService;
    private final OtpService otpService;
    private final edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones;

    public RenovarTokenUseCaseImpl(JwtService jwtService, OtpService otpService) {
        this(jwtService, otpService, null);
    }

    public RenovarTokenUseCaseImpl(JwtService jwtService, OtpService otpService, edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones) {
        this.sesiones = sesiones;
        this.jwtService = jwtService;
        this.otpService = otpService;
    }

    @Override
    public SesionAutenticada renovar(String tokenRecibido) {
        String token = tokenRecibido != null ? tokenRecibido.trim() : null;
        if (token == null || token.isBlank() || otpService.esTokenInvalido(token)) {
            log.warn("Intento de renovación con token inválido, expirado o revocado");
            throw CredencialesIncorrectasException.tokenInvalido(TOKEN_NO_VALIDO);
        }

        Claims claims = validar(token);
        Long usuarioId = jwtService.obtenerUsuarioId(token);
        Set<Rol> roles = jwtService.obtenerRoles(token);
        if (usuarioId == null || roles == null || roles.isEmpty()) {
            throw CredencialesIncorrectasException.tokenInvalido(
                    "El token presentado no contiene información de identidad válida");
        }

        long emitidoEn = claims != null && claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : 0L;
        if (otpService.estaUsuarioRevocado(usuarioId, emitidoEn)) {
            log.warn("Renovación rechazada para usuario ID {} por revocación global de cuenta", usuarioId);
            throw CredencialesIncorrectasException.tokenInvalido(TOKEN_NO_VALIDO);
        }

        long restante = jwtService.obtenerTiempoRestanteSegundos(token);
        if (restante > 0) {
            otpService.invalidarToken(token, restante);
        }

        Rol rol = roles.iterator().next();
        String nuevoToken = jwtService.generarToken(usuarioId, Set.of(rol));
        if (sesiones != null) {
            sesiones.renovar(token, nuevoToken);
        }
        log.info("Token JWT renovado exitosamente con rotación para usuario ID {}", usuarioId);
        return SesionAutenticada.conToken(nuevoToken, jwtService.obtenerVigenciaSegundos(), usuarioId, rol, null);
    }

    private Claims validar(String token) {
        try {
            return jwtService.validarToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Intento de renovación con token JWT inválido o expirado");
            throw CredencialesIncorrectasException.tokenInvalido(TOKEN_NO_VALIDO);
        }
    }
}
