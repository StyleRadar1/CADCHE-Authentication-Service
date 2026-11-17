package edu.dosw.styleradar.auth.application.usecase;

import java.time.Clock;
import java.time.Instant;

import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.model.UsuarioRegistrado;
import edu.dosw.styleradar.auth.domain.port.in.RegistrarUsuarioUseCase;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro de soporte solo para dev/test. El registro real lo hace ms-usuarios (Database-per-Service);
 * en QA y PROD responde fail-closed con 503.
 */
@Slf4j
public class RegistrarUsuarioUseCaseImpl implements RegistrarUsuarioUseCase {

    static final long ID_USUARIO_STUB = 12L;

    private final boolean habilitado;
    private final Clock clock;

    public RegistrarUsuarioUseCaseImpl(boolean habilitado, Clock clock) {
        this.habilitado = habilitado;
        this.clock = clock;
    }

    @Override
    public UsuarioRegistrado registrar(String nombre, String email, String telefono) {
        String correo = SecuritySanitizer.normalizeEmail(email);
        if (!habilitado) {
            log.warn("Intento de registro en modo fail-closed bloqueado para {}", SecuritySanitizer.maskEmail(correo));
            throw new ServicioExternoNoDisponibleException(
                    "El registro directo no está disponible en este entorno hasta completar la sincronización con ms-usuarios");
        }
        log.info("Registro de usuario de soporte solicitado para: {}", SecuritySanitizer.maskEmail(correo));
        return new UsuarioRegistrado(ID_USUARIO_STUB, nombre.trim(), correo, telefono, Instant.now(clock), Rol.COMPRADOR);
    }
}
