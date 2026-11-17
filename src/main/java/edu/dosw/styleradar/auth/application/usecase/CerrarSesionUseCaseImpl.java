package edu.dosw.styleradar.auth.application.usecase;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.port.in.CerrarSesionUseCase;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;

/**
 * Cierre de sesión: el token queda en la lista negra de Redis hasta que vence.
 */
@Slf4j
public class CerrarSesionUseCaseImpl implements CerrarSesionUseCase {

    private final JwtService jwtService;
    private final OtpService otpService;

    public CerrarSesionUseCaseImpl(JwtService jwtService, OtpService otpService) {
        this.jwtService = jwtService;
        this.otpService = otpService;
    }

    @Override
    public void cerrarSesion(String token) {
        if (token == null || token.isBlank()) {
            throw CredencialesIncorrectasException.tokenInvalido("No autorizado: token vacío o malformado");
        }
        try {
            jwtService.validarToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Se intentó cerrar sesión con un token inválido o expirado");
            throw CredencialesIncorrectasException.tokenInvalido("El token presentado es inválido o expirado");
        }

        long tiempoRestante = jwtService.obtenerTiempoRestanteSegundos(token);
        if (tiempoRestante > 0) {
            otpService.invalidarToken(token, tiempoRestante);
            log.info("Token revocado en logout con TTL restante de {}s", tiempoRestante);
        }
    }
}
