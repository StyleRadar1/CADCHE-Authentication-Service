package edu.dosw.styleradar.auth.application.usecase;

import java.util.Set;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.model.EntidadAsociada;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;
import edu.dosw.styleradar.auth.domain.port.in.RenovarTokenUseCase;
import edu.dosw.styleradar.auth.domain.port.out.EntidadAsociadaPort;
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
    private final EntidadAsociadaPort entidadAsociadaPort;

    public RenovarTokenUseCaseImpl(JwtService jwtService, OtpService otpService) {
        this(jwtService, otpService, null, null);
    }

    public RenovarTokenUseCaseImpl(JwtService jwtService, OtpService otpService, edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones) {
        this(jwtService, otpService, sesiones, null);
    }

    public RenovarTokenUseCaseImpl(
            JwtService jwtService,
            OtpService otpService,
            edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones,
            EntidadAsociadaPort entidadAsociadaPort) {
        this.sesiones = sesiones;
        this.jwtService = jwtService;
        this.otpService = otpService;
        this.entidadAsociadaPort = entidadAsociadaPort;
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
        Set<Rol> rolesEmitidos = Set.of(rol);
        EntidadAsociada entidad = resolverEntidadParaRenovacion(usuarioId, rolesEmitidos, claims);
        String nuevoToken = entidad.estaVacia()
                ? jwtService.generarToken(usuarioId, rolesEmitidos)
                : jwtService.generarToken(usuarioId, rolesEmitidos, entidad.storeId(), entidad.foundationId());
        if (sesiones != null) {
            sesiones.renovar(token, nuevoToken);
        }
        log.info("Token JWT renovado exitosamente con rotación para usuario ID {}", usuarioId);
        return SesionAutenticada.conToken(nuevoToken, jwtService.obtenerVigenciaSegundos(), usuarioId, rol, null);
    }

    private EntidadAsociada resolverEntidadParaRenovacion(Long usuarioId, Set<Rol> roles, Claims claims) {
        EntidadAsociada consultada = entidadAsociadaPort != null
                ? entidadAsociadaPort.resolverEntidadAsociada(usuarioId, roles)
                : EntidadAsociada.VACIA;

        String storeId = consultada != null && consultada.storeId() != null && !consultada.storeId().isBlank()
                ? consultada.storeId()
                : leerStoreIdDeClaims(claims);
        Long foundationId = consultada != null && consultada.foundationId() != null
                ? consultada.foundationId()
                : leerFoundationIdDeClaims(claims);

        return new EntidadAsociada(storeId, foundationId);
    }

    private static String leerStoreIdDeClaims(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object raw = claims.get("storeId");
        if (raw == null) {
            return null;
        }
        String valor = String.valueOf(raw).trim();
        return valor.isBlank() ? null : valor;
    }

    private static Long leerFoundationIdDeClaims(Claims claims) {
        if (claims == null) {
            return null;
        }
        Object raw = claims.get("foundationId");
        if (raw instanceof Number numero) {
            return numero.longValue();
        }
        if (raw instanceof String texto && !texto.isBlank()) {
            try {
                return Long.valueOf(texto.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
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
