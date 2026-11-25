package edu.dosw.styleradar.auth.application.usecase;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.application.service.SesionesActivasService;
import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;
import edu.dosw.styleradar.auth.domain.model.IdentidadUsuario;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.port.out.UsuarioIdentidadPort;
import io.jsonwebtoken.Jwts;

@DisplayName("Los casos de uso de sesión avisan al registro de sesiones (user.session.changed)")
class SesionesEnCasosDeUsoTest {

    private JwtService jwt;
    private OtpService otp;
    private UsuarioIdentidadPort usuarios;
    private SesionesActivasService sesiones;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtService.class);
        otp = mock(OtpService.class);
        usuarios = mock(UsuarioIdentidadPort.class);
        sesiones = mock(SesionesActivasService.class);
        when(jwt.generarToken(eq(12L), any())).thenReturn("token-nuevo");
    }

    @Test
    @DisplayName("Login con correo verificado inicia la sesión")
    void loginDirecto() {
        when(usuarios.verificarCredenciales("ana@styleradar.com", "Clave*2026"))
                .thenReturn(new IdentidadUsuario(12L, "ana@styleradar.com", Rol.BUYER, EstadoCuenta.ACTIVA, true));

        new IniciarSesionUseCaseImpl(usuarios, otp, mock(EmailService.class), jwt, sesiones)
                .iniciarSesion("ana@styleradar.com", "Clave*2026");

        verify(sesiones).iniciar("token-nuevo");
    }

    @Test
    @DisplayName("Verificar el OTP inicia la sesión")
    void otp() {
        when(otp.obtenerUsuarioId("ana@styleradar.com")).thenReturn(12L);
        when(otp.obtenerRol("ana@styleradar.com")).thenReturn("BUYER");

        new VerificarOtpUseCaseImpl(otp, jwt, usuarios, sesiones).verificar("ana@styleradar.com", "123456");

        verify(sesiones).iniciar("token-nuevo");
    }

    @Test
    @DisplayName("Renovar traslada la sesión al token nuevo")
    void renovar() {
        when(jwt.validarToken("token-viejo")).thenReturn(Jwts.claims().subject("12")
                .issuedAt(Date.from(Instant.parse("2026-11-25T10:00:00Z"))).build());
        when(jwt.obtenerUsuarioId("token-viejo")).thenReturn(12L);
        when(jwt.obtenerRoles("token-viejo")).thenReturn(Set.of(Rol.BUYER));
        when(jwt.obtenerTiempoRestanteSegundos("token-viejo")).thenReturn(600L);
        when(otp.estaUsuarioRevocado(eq(12L), anyLong())).thenReturn(false);

        new RenovarTokenUseCaseImpl(jwt, otp, sesiones).renovar("token-viejo");

        verify(sesiones).renovar("token-viejo", "token-nuevo");
    }

    @Test
    @DisplayName("Cerrar sesión registra el cierre")
    void cerrar() {
        when(jwt.validarToken("token-1")).thenReturn(Jwts.claims().subject("12").build());
        when(jwt.obtenerTiempoRestanteSegundos("token-1")).thenReturn(600L);

        new CerrarSesionUseCaseImpl(jwt, otp, sesiones).cerrarSesion("token-1");

        verify(sesiones).cerrar("token-1");
    }

    @Test
    @DisplayName("Inactivar la cuenta cierra todas sus sesiones")
    void revocar() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-25T15:00:00Z"), ZoneOffset.UTC);

        new RevocarSesionesUsuarioUseCaseImpl(otp, reloj, sesiones)
                .revocar("evento-1", 12L, EstadoCuenta.INACTIVA, null);

        verify(sesiones).revocarUsuario(12L);
    }
}
