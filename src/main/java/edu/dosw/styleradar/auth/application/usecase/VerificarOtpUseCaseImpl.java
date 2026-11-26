package edu.dosw.styleradar.auth.application.usecase;

import java.util.Set;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;
import edu.dosw.styleradar.auth.domain.port.in.VerificarOtpUseCase;
import edu.dosw.styleradar.auth.domain.port.out.UsuarioIdentidadPort;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import lombok.extern.slf4j.Slf4j;

/**
 * SR-RF-92. Verifica el código, marca el correo como verificado en ms-usuarios y emite la sesión.
 */
@Slf4j
public class VerificarOtpUseCaseImpl implements VerificarOtpUseCase {

    private final OtpService otpService;
    private final JwtService jwtService;
    private final UsuarioIdentidadPort usuarioIdentidadPort;
    private final edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones;

    public VerificarOtpUseCaseImpl(
            OtpService otpService, JwtService jwtService, UsuarioIdentidadPort usuarioIdentidadPort) {
        this(otpService, jwtService, usuarioIdentidadPort, null);
    }

    public VerificarOtpUseCaseImpl(OtpService otpService, JwtService jwtService,
            UsuarioIdentidadPort usuarioIdentidadPort, edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones) {
        this.sesiones = sesiones;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.usuarioIdentidadPort = usuarioIdentidadPort;
    }

    @Override
    public SesionAutenticada verificar(String email, String codigoOtp) {
        String correo = SecuritySanitizer.normalizeEmail(email);
        log.info("Verificación de OTP solicitada para: {}", SecuritySanitizer.maskEmail(correo));

        Long usuarioId = otpService.obtenerUsuarioId(correo);
        String nombreRol = otpService.obtenerRol(correo);
        if (usuarioId == null || nombreRol == null) {
            // Contrato 4.1: en /otp/verify un código sin verificación en curso (ya usado, anulado o vencido)
            // responde OTP_EXPIRED; OTP_SESSION_NOT_FOUND es solo para /otp/resend.
            throw new OtpExpiradoException("El código OTP expiró o no existe. Debe solicitar uno nuevo.");
        }

        otpService.verificarOtp(correo, codigoOtp);

        Rol rol = Rol.desde(nombreRol);
        String token = jwtService.generarToken(usuarioId, Set.of(rol));
        otpService.eliminarOtp(correo);
        usuarioIdentidadPort.marcarCorreoVerificado(usuarioId);
        if (sesiones != null) {
            sesiones.iniciar(token);
        }

        log.info("Correo verificado y token JWT emitido para usuario ID {}", usuarioId);
        return SesionAutenticada.conToken(token, jwtService.obtenerVigenciaSegundos(), usuarioId, rol, correo);
    }
}
