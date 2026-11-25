package edu.dosw.styleradar.auth.infrastructure.config;

import java.time.Clock;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.application.service.SesionesActivasService;
import edu.dosw.styleradar.auth.application.service.impl.OtpServiceImpl;
import edu.dosw.styleradar.auth.application.usecase.CerrarSesionUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.IniciarSesionUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.ReenviarOtpUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.RenovarTokenUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.RestablecerContrasenaUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.RevocarSesionesUsuarioUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.VerificarOtpUseCaseImpl;
import edu.dosw.styleradar.auth.domain.port.in.CerrarSesionUseCase;
import edu.dosw.styleradar.auth.domain.port.in.IniciarSesionUseCase;
import edu.dosw.styleradar.auth.domain.port.in.ReenviarOtpUseCase;
import edu.dosw.styleradar.auth.domain.port.in.RenovarTokenUseCase;
import edu.dosw.styleradar.auth.domain.port.in.RestablecerContrasenaUseCase;
import edu.dosw.styleradar.auth.domain.port.in.RevocarSesionesUsuarioUseCase;
import edu.dosw.styleradar.auth.domain.port.in.VerificarOtpUseCase;
import edu.dosw.styleradar.auth.domain.port.out.EntidadAsociadaPort;
import edu.dosw.styleradar.auth.domain.port.out.EventoSesionPublisherPort;
import edu.dosw.styleradar.auth.domain.port.out.RegistroSesionesPort;
import edu.dosw.styleradar.auth.domain.port.out.UsuarioIdentidadPort;

/**
 * Inversión de control: registra cada caso de uso ({@code <Nombre>UseCaseImpl}) contra su puerto de
 * entrada inyectando los puertos de salida y servicios que necesita. Los casos de uso no llevan
 * anotaciones de Spring (guía de arquitectura hexagonal StyleRadar, regla de oro 4).
 */
@Configuration
public class BeanConfig {

    /** Prefijo Redis de los códigos de restablecimiento de contraseña. */
    public static final String PREFIJO_RESTABLECIMIENTO = "reset:";

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public SesionesActivasService sesionesActivasService(JwtService jwtService,
            ObjectProvider<RegistroSesionesPort> registro, ObjectProvider<EventoSesionPublisherPort> eventos,
            Clock clock) {
        return new SesionesActivasService(jwtService, registro.getIfAvailable(), eventos.getIfAvailable(), clock);
    }

    @Bean
    public IniciarSesionUseCase iniciarSesionUseCase(
            UsuarioIdentidadPort usuarioIdentidadPort,
            OtpService otpService,
            EmailService emailService,
            JwtService jwtService,
            SesionesActivasService sesiones,
            ObjectProvider<EntidadAsociadaPort> entidadAsociada) {
        return new IniciarSesionUseCaseImpl(
                usuarioIdentidadPort, otpService, emailService, jwtService, sesiones, entidadAsociada.getIfAvailable());
    }

    @Bean
    public VerificarOtpUseCase verificarOtpUseCase(OtpService otpService, JwtService jwtService,
            UsuarioIdentidadPort usuarioIdentidadPort, SesionesActivasService sesiones,
            ObjectProvider<EntidadAsociadaPort> entidadAsociada) {
        return new VerificarOtpUseCaseImpl(
                otpService, jwtService, usuarioIdentidadPort, sesiones, entidadAsociada.getIfAvailable());
    }

    @Bean
    public ReenviarOtpUseCase reenviarOtpUseCase(OtpService otpService, EmailService emailService) {
        return new ReenviarOtpUseCaseImpl(otpService, emailService);
    }

    @Bean
    public RenovarTokenUseCase renovarTokenUseCase(JwtService jwtService, OtpService otpService,
            SesionesActivasService sesiones, ObjectProvider<EntidadAsociadaPort> entidadAsociada) {
        return new RenovarTokenUseCaseImpl(jwtService, otpService, sesiones, entidadAsociada.getIfAvailable());
    }

    @Bean
    public CerrarSesionUseCase cerrarSesionUseCase(JwtService jwtService, OtpService otpService,
            SesionesActivasService sesiones) {
        return new CerrarSesionUseCaseImpl(jwtService, otpService, sesiones);
    }

    @Bean
    public RevocarSesionesUsuarioUseCase revocarSesionesUsuarioUseCase(OtpService otpService, Clock clock,
            SesionesActivasService sesiones) {
        return new RevocarSesionesUsuarioUseCaseImpl(otpService, clock, sesiones);
    }

    /**
     * Restablecimiento de contraseña: usa un servicio OTP con prefijo propio ({@code reset:}) para que sus
     * códigos, intentos y bloqueos no se mezclen con los del login. Las dependencias llegan por
     * {@link ObjectProvider} para no exigirlas en los slices de prueba que no usan este flujo.
     */
    @Bean
    public RestablecerContrasenaUseCase restablecerContrasenaUseCase(
            ObjectProvider<UsuarioIdentidadPort> usuarios,
            ObjectProvider<OtpService> otpService,
            ObjectProvider<EmailService> emailService,
            ObjectProvider<StringRedisTemplate> redisTemplate,
            ObjectProvider<SesionesActivasService> sesiones,
            @Value("${styleradar.security.redis-local-fallback-enabled:false}") boolean localFallbackEnabled,
            Environment environment,
            Clock clock) {
        OtpService codigos = new OtpServiceImpl(redisTemplate.getIfAvailable(), localFallbackEnabled, environment,
                PREFIJO_RESTABLECIMIENTO);
        return new RestablecerContrasenaUseCaseImpl(usuarios.getIfAvailable(), codigos, otpService.getIfAvailable(),
                emailService.getIfAvailable(), sesiones.getIfAvailable(), clock);
    }
}
