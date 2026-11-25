package edu.dosw.styleradar.auth.application.usecase;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.application.service.SesionesActivasService;
import edu.dosw.styleradar.auth.domain.exception.OtpBloqueadoTemporalmenteException;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.model.IdentidadUsuario;
import edu.dosw.styleradar.auth.domain.model.SolicitudRestablecimiento;
import edu.dosw.styleradar.auth.domain.port.in.RestablecerContrasenaUseCase;
import edu.dosw.styleradar.auth.domain.port.out.UsuarioIdentidadPort;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

/**
 * Restablecimiento de contraseña con OTP, siguiendo el OWASP Forgot Password Cheat Sheet:
 * <ul>
 *   <li>La solicitud responde lo mismo exista o no la cuenta (sin enumeración de usuarios).</li>
 *   <li>Código de 6 dígitos con {@code SecureRandom}, guardado como hash SHA-256 salteado, válido 5 min,
 *       3 intentos y espera de 60 s; códigos propios (prefijo {@code reset:}), separados de los del login.</li>
 *   <li>Como máximo 4 códigos por correo cada 15 min, contados en Redis (evita el bombardeo de correos).</li>
 *   <li>No inicia sesión: al cambiar la contraseña revoca todas las sesiones y avisa por correo.</li>
 * </ul>
 */
@Slf4j
public class RestablecerContrasenaUseCaseImpl implements RestablecerContrasenaUseCase {

    /** Como máximo 1 código y 3 reenvíos por correo cada 15 minutos. */
    static final int MAXIMO_CODIGOS = 4;
    static final long VENTANA_SEGUNDOS = 900L;

    private final UsuarioIdentidadPort usuarios;
    private final OtpService codigos;
    private final OtpService sesionesOtp;
    private final EmailService emailService;
    private final SesionesActivasService sesiones;
    private final Clock clock;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Servicios administrados por Spring e inyectados por constructor.")
    public RestablecerContrasenaUseCaseImpl(UsuarioIdentidadPort usuarios, OtpService codigos, OtpService sesionesOtp,
            EmailService emailService, SesionesActivasService sesiones, Clock clock) {
        this.usuarios = usuarios;
        this.codigos = codigos;
        this.sesionesOtp = sesionesOtp;
        this.emailService = emailService;
        this.sesiones = sesiones;
        this.clock = clock;
    }

    @Override
    public SolicitudRestablecimiento solicitar(String email) {
        String correo = SecuritySanitizer.normalizeEmail(email);
        String enmascarado = SecuritySanitizer.maskEmail(correo);
        Optional<IdentidadUsuario> cuenta = usuarios.buscarPorCorreo(correo);
        if (cuenta.isEmpty() || !cuenta.get().estaActiva()) {
            log.info("Restablecimiento solicitado para {} sin cuenta activa; no se envía código", enmascarado);
            return respuesta(correo);
        }
        IdentidadUsuario usuario = cuenta.get();
        if (!codigos.registrarSolicitud(correo, MAXIMO_CODIGOS, VENTANA_SEGUNDOS)) {
            log.warn("Restablecimiento para {} sin nuevo código: límite de {} códigos cada {} s", enmascarado,
                    MAXIMO_CODIGOS, VENTANA_SEGUNDOS);
            return respuesta(correo);
        }
        String codigo;
        try {
            codigo = codigos.obtenerUsuarioId(correo) != null
                    ? codigos.reenviarOtp(correo)
                    : codigos.generarOtp(correo, usuario.id(), usuario.rol().name());
        } catch (OtpBloqueadoTemporalmenteException | OtpIntentosAgotadosException | OtpExpiradoException ex) {
            log.warn("Restablecimiento para {} sin nuevo código: {}", enmascarado,
                    SecuritySanitizer.sanitizeForLog(ex.getMessage()));
            return respuesta(correo);
        }
        try {
            emailService.enviarCodigoRestablecimiento(correo, codigo);
        } catch (RuntimeException ex) {
            codigos.eliminarOtp(correo);
            throw ex;
        }
        log.info("Código de restablecimiento enviado para usuario ID {}", usuario.id());
        return respuesta(correo);
    }

    @Override
    public void restablecer(String email, String codigo, String nuevaContrasena) {
        String correo = SecuritySanitizer.normalizeEmail(email);
        Long usuarioId = codigos.obtenerUsuarioId(correo);
        if (usuarioId == null) {
            throw new OtpExpiradoException("El código expiró o no existe. Solicita uno nuevo.");
        }
        codigos.verificarOtp(correo, codigo);

        usuarios.cambiarContrasena(usuarioId, nuevaContrasena);
        codigos.eliminarOtp(correo);

        // Toda sesión abierta antes del cambio deja de servir (incluida la de un posible atacante).
        sesionesOtp.revocarSesionesUsuario(usuarioId, Instant.now(clock));
        if (sesiones != null) {
            sesiones.revocarUsuario(usuarioId);
        }
        // Recibir el código demuestra que el usuario controla el correo.
        usuarios.marcarCorreoVerificado(usuarioId);
        try {
            emailService.enviarAvisoContrasenaCambiada(correo);
        } catch (RuntimeException ex) {
            log.warn("La contraseña del usuario ID {} cambió, pero no se pudo enviar el aviso por correo: {}",
                    usuarioId, SecuritySanitizer.sanitizeForLog(ex.getMessage()));
        }
        log.warn("Contraseña restablecida y sesiones revocadas para usuario ID {}", usuarioId);
    }

    private SolicitudRestablecimiento respuesta(String correo) {
        return new SolicitudRestablecimiento(correo, codigos.obtenerVigenciaOtpSegundos());
    }
}
