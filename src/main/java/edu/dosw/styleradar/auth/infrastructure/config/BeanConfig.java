package edu.dosw.styleradar.auth.infrastructure.config;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.application.usecase.CerrarSesionUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.IniciarSesionUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.ReenviarOtpUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.RegistrarUsuarioUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.RenovarTokenUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.RevocarSesionesUsuarioUseCaseImpl;
import edu.dosw.styleradar.auth.application.usecase.VerificarOtpUseCaseImpl;
import edu.dosw.styleradar.auth.domain.port.in.CerrarSesionUseCase;
import edu.dosw.styleradar.auth.domain.port.in.IniciarSesionUseCase;
import edu.dosw.styleradar.auth.domain.port.in.ReenviarOtpUseCase;
import edu.dosw.styleradar.auth.domain.port.in.RegistrarUsuarioUseCase;
import edu.dosw.styleradar.auth.domain.port.in.RenovarTokenUseCase;
import edu.dosw.styleradar.auth.domain.port.in.RevocarSesionesUsuarioUseCase;
import edu.dosw.styleradar.auth.domain.port.in.VerificarOtpUseCase;
import edu.dosw.styleradar.auth.domain.port.out.UsuarioIdentidadPort;

/**
 * Inversión de control: registra cada caso de uso ({@code <Nombre>UseCaseImpl}) contra su puerto de
 * entrada inyectando los puertos de salida y servicios que necesita. Los casos de uso no llevan
 * anotaciones de Spring (guía de arquitectura hexagonal StyleRadar, regla de oro 4).
 */
@Configuration
public class BeanConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public IniciarSesionUseCase iniciarSesionUseCase(
            UsuarioIdentidadPort usuarioIdentidadPort,
            OtpService otpService,
            EmailService emailService,
            JwtService jwtService) {
        return new IniciarSesionUseCaseImpl(usuarioIdentidadPort, otpService, emailService, jwtService);
    }

    @Bean
    public VerificarOtpUseCase verificarOtpUseCase(
            OtpService otpService, JwtService jwtService, UsuarioIdentidadPort usuarioIdentidadPort) {
        return new VerificarOtpUseCaseImpl(otpService, jwtService, usuarioIdentidadPort);
    }

    @Bean
    public ReenviarOtpUseCase reenviarOtpUseCase(OtpService otpService, EmailService emailService) {
        return new ReenviarOtpUseCaseImpl(otpService, emailService);
    }

    @Bean
    public RenovarTokenUseCase renovarTokenUseCase(JwtService jwtService, OtpService otpService) {
        return new RenovarTokenUseCaseImpl(jwtService, otpService);
    }

    @Bean
    public CerrarSesionUseCase cerrarSesionUseCase(JwtService jwtService, OtpService otpService) {
        return new CerrarSesionUseCaseImpl(jwtService, otpService);
    }

    @Bean
    public RevocarSesionesUsuarioUseCase revocarSesionesUsuarioUseCase(OtpService otpService, Clock clock) {
        return new RevocarSesionesUsuarioUseCaseImpl(otpService, clock);
    }

    /**
     * El registro de soporte solo se habilita con la propiedad explícita y en los perfiles dev/test;
     * en QA y PROD siempre responde fail-closed (el registro real es de ms-usuarios).
     */
    @Bean
    public RegistrarUsuarioUseCase registrarUsuarioUseCase(
            @Value("${styleradar.security.registration.stub-enabled:false}") boolean stubEnabled,
            Environment environment,
            Clock clock) {
        boolean habilitado = stubEnabled
                && environment.acceptsProfiles(Profiles.of("dev", "test"))
                && !environment.acceptsProfiles(Profiles.of("prod", "qa"));
        return new RegistrarUsuarioUseCaseImpl(habilitado, clock);
    }
}
