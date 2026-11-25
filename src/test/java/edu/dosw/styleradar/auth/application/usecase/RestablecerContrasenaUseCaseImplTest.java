package edu.dosw.styleradar.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.application.service.SesionesActivasService;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.OtpBloqueadoTemporalmenteException;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;
import edu.dosw.styleradar.auth.domain.model.IdentidadUsuario;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.model.SolicitudRestablecimiento;
import edu.dosw.styleradar.auth.domain.port.out.UsuarioIdentidadPort;

@DisplayName("RestablecerContrasenaUseCase: solicitud sin enumeración y cambio con revocación de sesiones")
class RestablecerContrasenaUseCaseImplTest {

    private static final String CORREO = "tienda@almacenaliado.co";
    private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");

    private UsuarioIdentidadPort usuarios;
    private OtpService codigos;
    private OtpService sesionesOtp;
    private EmailService email;
    private SesionesActivasService sesiones;
    private RestablecerContrasenaUseCaseImpl useCase;

    @BeforeEach
    void preparar() {
        usuarios = mock(UsuarioIdentidadPort.class);
        codigos = mock(OtpService.class);
        sesionesOtp = mock(OtpService.class);
        email = mock(EmailService.class);
        sesiones = mock(SesionesActivasService.class);
        useCase = new RestablecerContrasenaUseCaseImpl(usuarios, codigos, sesionesOtp, email, sesiones,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        when(codigos.obtenerVigenciaOtpSegundos()).thenReturn(300L);
        when(codigos.registrarSolicitud(anyString(), anyInt(), anyLong())).thenReturn(true);
        // Mockito devuelve 0L para Long; sin solicitud vigente el servicio real devuelve null.
        when(codigos.obtenerUsuarioId(anyString())).thenReturn(null);
    }

    private static IdentidadUsuario cuenta(EstadoCuenta estado) {
        return new IdentidadUsuario(13L, CORREO, Rol.STORE_ADMIN, estado, false);
    }

    @Test
    void cuentaActivaSinCodigoPrevioGeneraUnoYLoEnvia() {
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));
        when(codigos.generarOtp(CORREO, 13L, "STORE_ADMIN")).thenReturn("482915");

        SolicitudRestablecimiento r = useCase.solicitar(" Tienda@AlmacenAliado.co ");

        assertThat(r).isEqualTo(new SolicitudRestablecimiento(CORREO, 300L));
        verify(email).enviarCodigoRestablecimiento(CORREO, "482915");
        verify(codigos).registrarSolicitud(CORREO, RestablecerContrasenaUseCaseImpl.MAXIMO_CODIGOS,
                RestablecerContrasenaUseCaseImpl.VENTANA_SEGUNDOS);
    }

    @Test
    void conCodigoVigenteReenviaUnoNuevo() {
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));
        when(codigos.obtenerUsuarioId(CORREO)).thenReturn(13L);
        when(codigos.reenviarOtp(CORREO)).thenReturn("111222");

        useCase.solicitar(CORREO);

        verify(codigos, never()).generarOtp(anyString(), anyLong(), anyString());
        verify(email).enviarCodigoRestablecimiento(CORREO, "111222");
    }

    @Test
    void sinCuentaOCuentaNoActivaRespondeIgualSinEnviarNada() {
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.empty());
        assertThat(useCase.solicitar(CORREO)).isEqualTo(new SolicitudRestablecimiento(CORREO, 300L));
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.of(cuenta(EstadoCuenta.BLOQUEADA)));
        assertThat(useCase.solicitar(CORREO)).isEqualTo(new SolicitudRestablecimiento(CORREO, 300L));

        verify(email, never()).enviarCodigoRestablecimiento(anyString(), anyString());
        verify(codigos, never()).registrarSolicitud(anyString(), anyInt(), anyLong());
    }

    @Test
    void limiteDeCodigosEsperaYReenviosAgotadosRespondenIgualSinCorreo() {
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));
        when(codigos.registrarSolicitud(anyString(), anyInt(), anyLong())).thenReturn(false);
        useCase.solicitar(CORREO);

        when(codigos.registrarSolicitud(anyString(), anyInt(), anyLong())).thenReturn(true);
        when(codigos.generarOtp(CORREO, 13L, "STORE_ADMIN")).thenThrow(new OtpBloqueadoTemporalmenteException(40));
        useCase.solicitar(CORREO);

        when(codigos.obtenerUsuarioId(CORREO)).thenReturn(13L);
        when(codigos.reenviarOtp(CORREO)).thenThrow(OtpIntentosAgotadosException.reenviosAgotados());
        assertThat(useCase.solicitar(CORREO).expiraEnSegundos()).isEqualTo(300L);

        verify(email, never()).enviarCodigoRestablecimiento(anyString(), anyString());
    }

    @Test
    void siElCorreoFallaSeAnulaElCodigoYSePropagaEl503() {
        when(usuarios.buscarPorCorreo(CORREO)).thenReturn(Optional.of(cuenta(EstadoCuenta.ACTIVA)));
        when(codigos.generarOtp(CORREO, 13L, "STORE_ADMIN")).thenReturn("482915");
        doThrow(new ServicioExternoNoDisponibleException("SMTP caído"))
                .when(email).enviarCodigoRestablecimiento(CORREO, "482915");

        assertThatThrownBy(() -> useCase.solicitar(CORREO)).isInstanceOf(ServicioExternoNoDisponibleException.class);
        verify(codigos).eliminarOtp(CORREO);
    }

    @Test
    void restablecerCambiaLaContrasenaRevocaSesionesYAvisa() {
        when(codigos.obtenerUsuarioId(CORREO)).thenReturn(13L);

        useCase.restablecer(CORREO, "482915", "NuevaClave2026*");

        InOrder orden = inOrder(codigos, usuarios, sesionesOtp, sesiones, email);
        orden.verify(codigos).verificarOtp(CORREO, "482915");
        orden.verify(usuarios).cambiarContrasena(13L, "NuevaClave2026*");
        orden.verify(codigos).eliminarOtp(CORREO);
        orden.verify(sesionesOtp).revocarSesionesUsuario(13L, AHORA);
        orden.verify(sesiones).revocarUsuario(13L);
        orden.verify(usuarios).marcarCorreoVerificado(13L);
        orden.verify(email).enviarAvisoContrasenaCambiada(CORREO);
    }

    @Test
    void sinSolicitudVigenteOCodigoIncorrectoNoSeCambiaNada() {
        assertThatThrownBy(() -> useCase.restablecer(CORREO, "482915", "NuevaClave2026*"))
                .isInstanceOf(OtpExpiradoException.class);

        when(codigos.obtenerUsuarioId(CORREO)).thenReturn(13L);
        doThrow(new OtpInvalidoException(2))
                .when(codigos).verificarOtp(CORREO, "000000");
        assertThatThrownBy(() -> useCase.restablecer(CORREO, "000000", "NuevaClave2026*"))
                .isInstanceOf(OtpInvalidoException.class);

        verify(usuarios, never()).cambiarContrasena(anyLong(), anyString());
        verify(sesionesOtp, never()).revocarSesionesUsuario(anyLong(), eq(AHORA));
    }

    @Test
    void cuentaYaNoActivaNoRevocaNiAvisa() {
        when(codigos.obtenerUsuarioId(CORREO)).thenReturn(13L);
        doThrow(new CuentaNoActivaException("La cuenta se encuentra inactiva o bloqueada"))
                .when(usuarios).cambiarContrasena(13L, "NuevaClave2026*");

        assertThatThrownBy(() -> useCase.restablecer(CORREO, "482915", "NuevaClave2026*"))
                .isInstanceOf(CuentaNoActivaException.class);
        verify(email, never()).enviarAvisoContrasenaCambiada(anyString());
    }

    @Test
    void elAvisoPorCorreoNoBloqueaElCambioYFuncionaSinRegistroDeSesiones() {
        RestablecerContrasenaUseCaseImpl sinRegistro = new RestablecerContrasenaUseCaseImpl(usuarios, codigos,
                sesionesOtp, email, null, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(codigos.obtenerUsuarioId(CORREO)).thenReturn(13L);
        doThrow(new ServicioExternoNoDisponibleException("SMTP caído")).when(email).enviarAvisoContrasenaCambiada(CORREO);

        sinRegistro.restablecer(CORREO, "482915", "NuevaClave2026*");

        verify(usuarios).cambiarContrasena(13L, "NuevaClave2026*");
        verify(sesionesOtp).revocarSesionesUsuario(13L, AHORA);
    }
}
