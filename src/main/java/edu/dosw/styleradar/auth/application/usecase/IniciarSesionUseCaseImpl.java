package edu.dosw.styleradar.auth.application.usecase;

import java.util.Set;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.model.IdentidadUsuario;
import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;
import edu.dosw.styleradar.auth.domain.port.in.IniciarSesionUseCase;
import edu.dosw.styleradar.auth.domain.port.out.UsuarioIdentidadPort;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import lombok.extern.slf4j.Slf4j;

/**
 * SR-RF-02. Valida las credenciales con ms-usuarios y bifurca según el correo esté verificado (RN-05):
 * con correo verificado entrega el token de una vez; si no, envía el código OTP (SR-RF-92).
 */
@Slf4j
public class IniciarSesionUseCaseImpl implements IniciarSesionUseCase {

    private final UsuarioIdentidadPort usuarioIdentidadPort;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final NotificadorCodigoOtp notificador;
    private final edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones;

    public IniciarSesionUseCaseImpl(
            UsuarioIdentidadPort usuarioIdentidadPort,
            OtpService otpService,
            EmailService emailService,
            JwtService jwtService) {
        this(usuarioIdentidadPort, otpService, emailService, jwtService, null);
    }

    public IniciarSesionUseCaseImpl(
            UsuarioIdentidadPort usuarioIdentidadPort,
            OtpService otpService,
            EmailService emailService,
            JwtService jwtService,
            edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones) {
        this.sesiones = sesiones;
        this.usuarioIdentidadPort = usuarioIdentidadPort;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.notificador = new NotificadorCodigoOtp(emailService, otpService);
    }

    @Override
    public SesionAutenticada iniciarSesion(String email, String password) {
        String correo = SecuritySanitizer.normalizeEmail(email);
        log.info("Intento de autenticación para usuario: {}", SecuritySanitizer.maskEmail(correo));

        IdentidadUsuario usuario = usuarioIdentidadPort.verificarCredenciales(correo, password);
        if (!usuario.estaActiva()) {
            log.warn("Inicio de sesión rechazado por cuenta no activa para usuario ID {}", usuario.id());
            throw new CuentaNoActivaException("La cuenta se encuentra inactiva o bloqueada");
        }

        long vigenciaToken = jwtService.obtenerVigenciaSegundos();
        if (usuario.correoVerificado()) {
            String token = jwtService.generarToken(usuario.id(), Set.of(usuario.rol()));
            if (sesiones != null) {
                sesiones.iniciar(token);
            }
            log.info("Inicio de sesión directo sin OTP (correo verificado) para usuario ID {}", usuario.id());
            return SesionAutenticada.conToken(token, vigenciaToken, usuario.id(), usuario.rol(), correo);
        }

        String codigo = otpService.generarOtp(correo, usuario.id(), usuario.rol().name());
        notificador.enviar(correo, codigo);
        log.info("Correo sin verificar: código OTP enviado para usuario ID {}", usuario.id());
        return SesionAutenticada.pendienteDeVerificacion(
                vigenciaToken,
                usuario.id(),
                usuario.rol(),
                correo,
                otpService.obtenerVigenciaOtpSegundos(),
                otpService.obtenerMaxIntentos());
    }
}
