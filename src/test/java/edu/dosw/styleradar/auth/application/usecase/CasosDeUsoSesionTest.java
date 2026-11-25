package edu.dosw.styleradar.auth.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.CodigoError;
import edu.dosw.styleradar.auth.domain.model.DesafioOtp;
import edu.dosw.styleradar.auth.domain.model.EntidadAsociada;
import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;
import edu.dosw.styleradar.auth.domain.model.IdentidadUsuario;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;
import edu.dosw.styleradar.auth.domain.port.out.EntidadAsociadaPort;
import edu.dosw.styleradar.auth.domain.port.out.UsuarioIdentidadPort;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;

@DisplayName("Casos de uso de sesión (capa de aplicación, Mockito sin Spring)")
class CasosDeUsoSesionTest {

    private static final String CORREO = "comprador@styleradar.com";

    private UsuarioIdentidadPort usuarios;
    private OtpService otp;
    private EmailService email;
    private JwtService jwt;

    @BeforeEach
    void setUp() {
        usuarios = mock(UsuarioIdentidadPort.class);
        otp = mock(OtpService.class);
        email = mock(EmailService.class);
        jwt = mock(JwtService.class);
        when(jwt.obtenerVigenciaSegundos()).thenReturn(3600L);
        when(otp.obtenerVigenciaOtpSegundos()).thenReturn(300L);
        when(otp.obtenerMaxIntentos()).thenReturn(3);
    }

    @Nested
    @DisplayName("IniciarSesionUseCaseImpl (SR-RF-02)")
    class IniciarSesion {

        private IniciarSesionUseCaseImpl useCase;

        @BeforeEach
        void crear() {
            useCase = new IniciarSesionUseCaseImpl(usuarios, otp, email, jwt);
        }

        @Test
        @DisplayName("Normaliza el correo y entrega token directo si el correo está verificado")
        void correoVerificado_entregaToken() {
            when(usuarios.verificarCredenciales(CORREO, "Clave2026*"))
                    .thenReturn(new IdentidadUsuario(5L, CORREO, Rol.BUYER, EstadoCuenta.ACTIVA, true));
            when(jwt.generarToken(5L, Set.of(Rol.BUYER))).thenReturn("jwt");

            SesionAutenticada sesion = useCase.iniciarSesion("  Comprador@StyleRadar.com ", "Clave2026*");

            assertThat(sesion.accessToken()).isEqualTo("jwt");
            assertThat(sesion.expiraEnSegundos()).isEqualTo(3600L);
            assertThat(sesion.requiereOtp()).isFalse();
            assertThat(sesion.email()).isEqualTo(CORREO);
            verify(otp, never()).generarOtp(anyString(), anyLong(), anyString());
        }

        @Test
        @DisplayName("Con correo sin verificar envía el código y no entrega token")
        void correoSinVerificar_enviaCodigo() {
            when(usuarios.verificarCredenciales(CORREO, "Clave2026*"))
                    .thenReturn(new IdentidadUsuario(5L, CORREO, Rol.BUYER, EstadoCuenta.ACTIVA, false));
            when(otp.generarOtp(CORREO, 5L, "BUYER")).thenReturn("123456");

            SesionAutenticada sesion = useCase.iniciarSesion(CORREO, "Clave2026*");

            assertThat(sesion.accessToken()).isNull();
            assertThat(sesion.requiereOtp()).isTrue();
            assertThat(sesion.otpExpiraEnSegundos()).isEqualTo(300L);
            assertThat(sesion.intentosOtpRestantes()).isEqualTo(3);
            verify(email).enviarOtp(CORREO, "123456");
        }

        @Test
        @DisplayName("Si falla el correo anula la verificación (compensación) y propaga 503")
        void falloSmtp_compensaYPropaga() {
            when(usuarios.verificarCredenciales(CORREO, "Clave2026*"))
                    .thenReturn(new IdentidadUsuario(5L, CORREO, Rol.BUYER, EstadoCuenta.ACTIVA, false));
            when(otp.generarOtp(CORREO, 5L, "BUYER")).thenReturn("123456");
            doThrow(new ServicioExternoNoDisponibleException("SMTP")).when(email).enviarOtp(CORREO, "123456");

            assertThatThrownBy(() -> useCase.iniciarSesion(CORREO, "Clave2026*"))
                    .isInstanceOf(ServicioExternoNoDisponibleException.class);
            verify(otp).eliminarOtp(CORREO);
        }

        @Test
        @DisplayName("RN-03: estado distinto de ACTIVA o ausente nunca abre sesión")
        void cuentaNoActiva_rechaza() {
            when(usuarios.verificarCredenciales(CORREO, "Clave2026*"))
                    .thenReturn(new IdentidadUsuario(5L, CORREO, Rol.BUYER, null, true));

            assertThatThrownBy(() -> useCase.iniciarSesion(CORREO, "Clave2026*"))
                    .isInstanceOf(CuentaNoActivaException.class)
                    .extracting("codigo").isEqualTo(CodigoError.ACCOUNT_NOT_ACTIVE);
        }
    }

    @Nested
    @DisplayName("VerificarOtpUseCaseImpl y ReenviarOtpUseCaseImpl (SR-RF-92)")
    class Otp {

        @Test
        @DisplayName("Contrato 4.1: sin verificación en curso (código ya usado o vencido) responde OTP_EXPIRED sin consumir intentos")
        void sinSesion_noConsumeIntentos() {
            VerificarOtpUseCaseImpl useCase = new VerificarOtpUseCaseImpl(otp, jwt, usuarios);

            assertThatThrownBy(() -> useCase.verificar(CORREO, "123456"))
                    .isInstanceOf(OtpExpiradoException.class)
                    .hasMessage("El código OTP expiró o no existe. Debe solicitar uno nuevo.")
                    .extracting("codigo").isEqualTo(CodigoError.OTP_EXPIRED);
            verify(otp, never()).verificarOtp(anyString(), anyString());
        }

        @Test
        @DisplayName("Código correcto: emite token, anula la verificación y marca el correo en ms-usuarios")
        void codigoCorrecto_emiteTokenYMarcaCorreo() {
            when(otp.obtenerUsuarioId(CORREO)).thenReturn(9L);
            when(otp.obtenerRol(CORREO)).thenReturn("FOUNDATION_REPRESENTATIVE");
            when(jwt.generarToken(9L, Set.of(Rol.FOUNDATION_REPRESENTATIVE))).thenReturn("jwt-9");

            SesionAutenticada sesion = new VerificarOtpUseCaseImpl(otp, jwt, usuarios).verificar(CORREO, "123456");

            assertThat(sesion.accessToken()).isEqualTo("jwt-9");
            assertThat(sesion.correoVerificado()).isTrue();
            verify(otp).eliminarOtp(CORREO);
            verify(usuarios).marcarCorreoVerificado(9L);
        }

        @Test
        @DisplayName("Reenvío informa vigencia, intentos y reenvíos restantes")
        void reenvio_informaContadores() {
            when(otp.reenviarOtp(CORREO)).thenReturn("654321");
            when(otp.obtenerReenviosRestantes(CORREO)).thenReturn(1);

            DesafioOtp desafio = new ReenviarOtpUseCaseImpl(otp, email).reenviar(CORREO);

            assertThat(desafio).isEqualTo(new DesafioOtp(CORREO, 300L, 3, 1));
            verify(email).enviarOtp(CORREO, "654321");
        }
    }

    @Nested
    @DisplayName("RenovarTokenUseCaseImpl y CerrarSesionUseCaseImpl")
    class Tokens {

        @Test
        @DisplayName("Renovar rota el token y revoca el anterior por su tiempo restante")
        void renovar_rotaToken() {
            when(otp.esTokenInvalido("t1")).thenReturn(false);
            when(jwt.validarToken("t1")).thenReturn(Jwts.claims().subject("4").issuedAt(new Date(5000L)).build());
            when(jwt.obtenerUsuarioId("t1")).thenReturn(4L);
            when(jwt.obtenerRoles("t1")).thenReturn(Set.of(Rol.STYLERADAR_ADMIN));
            when(otp.estaUsuarioRevocado(4L, 5000L)).thenReturn(false);
            when(jwt.obtenerTiempoRestanteSegundos("t1")).thenReturn(120L);
            when(jwt.generarToken(4L, Set.of(Rol.STYLERADAR_ADMIN))).thenReturn("t2");

            SesionAutenticada sesion = new RenovarTokenUseCaseImpl(jwt, otp).renovar(" t1 ");

            assertThat(sesion.accessToken()).isEqualTo("t2");
            verify(otp).invalidarToken("t1", 120L);
        }

        @Test
        @DisplayName("Renovar con token mal formado o nulo responde INVALID_TOKEN")
        void renovar_tokenInvalido() {
            RenovarTokenUseCaseImpl useCase = new RenovarTokenUseCaseImpl(jwt, otp);
            when(jwt.validarToken("roto")).thenThrow(new MalformedJwtException("roto"));

            assertThatThrownBy(() -> useCase.renovar(null))
                    .extracting("codigo").isEqualTo(CodigoError.INVALID_TOKEN);
            assertThatThrownBy(() -> useCase.renovar("roto"))
                    .isInstanceOf(CredencialesIncorrectasException.class)
                    .extracting("codigo").isEqualTo(CodigoError.INVALID_TOKEN);
        }

        @Test
        @DisplayName("Cerrar sesión revoca el token; vacío o inválido responde INVALID_TOKEN")
        void cerrarSesion() {
            CerrarSesionUseCaseImpl useCase = new CerrarSesionUseCaseImpl(jwt, otp);
            when(jwt.obtenerTiempoRestanteSegundos("ok")).thenReturn(60L);
            when(jwt.validarToken("malo")).thenThrow(new IllegalArgumentException("malo"));

            useCase.cerrarSesion("ok");
            verify(otp).invalidarToken("ok", 60L);

            assertThatThrownBy(() -> useCase.cerrarSesion(" ")).isInstanceOf(CredencialesIncorrectasException.class);
            assertThatThrownBy(() -> useCase.cerrarSesion("malo")).isInstanceOf(CredencialesIncorrectasException.class);

            when(jwt.obtenerTiempoRestanteSegundos("vencido")).thenReturn(0L);
            useCase.cerrarSesion("vencido");
            verify(otp, never()).invalidarToken("vencido", 0L);
        }
    }
    @Test
    @DisplayName("BUG-005: Login, OTP y Refresh incluyen storeId y foundationId cuando EntidadAsociadaPort los resuelve")
    void flujosSesionIncluyenClaimsDeEntidadAsociada() {
        EntidadAsociadaPort entidadPort = mock(EntidadAsociadaPort.class);

        when(usuarios.verificarCredenciales("tienda@styleradar.com", "Clave2026*"))
                .thenReturn(new IdentidadUsuario(50L, "tienda@styleradar.com", Rol.STORE_ADMIN, EstadoCuenta.ACTIVA, true));
        when(entidadPort.resolverEntidadAsociada(50L, Set.of(Rol.STORE_ADMIN)))
                .thenReturn(new EntidadAsociada("900555666-1", null));
        when(jwt.generarToken(50L, Set.of(Rol.STORE_ADMIN), "900555666-1", null)).thenReturn("jwt-tienda");

        IniciarSesionUseCaseImpl loginUseCase = new IniciarSesionUseCaseImpl(usuarios, otp, email, jwt, null, entidadPort);
        SesionAutenticada sesionLogin = loginUseCase.iniciarSesion("tienda@styleradar.com", "Clave2026*");
        assertThat(sesionLogin.accessToken()).isEqualTo("jwt-tienda");

        when(otp.obtenerUsuarioId(CORREO)).thenReturn(60L);
        when(otp.obtenerRol(CORREO)).thenReturn("FOUNDATION_REPRESENTATIVE");
        when(entidadPort.resolverEntidadAsociada(60L, Set.of(Rol.FOUNDATION_REPRESENTATIVE)))
                .thenReturn(new EntidadAsociada(null, 12L));
        when(jwt.generarToken(60L, Set.of(Rol.FOUNDATION_REPRESENTATIVE), null, 12L)).thenReturn("jwt-fundacion");

        VerificarOtpUseCaseImpl otpUseCase = new VerificarOtpUseCaseImpl(otp, jwt, usuarios, null, entidadPort);
        SesionAutenticada sesionOtp = otpUseCase.verificar(CORREO, "123456");
        assertThat(sesionOtp.accessToken()).isEqualTo("jwt-fundacion");

        when(otp.esTokenInvalido("old-t")).thenReturn(false);
        when(jwt.validarToken("old-t")).thenReturn(Jwts.claims().subject("50")
                .add("storeId", "900555666-1")
                .issuedAt(new Date(5000L)).build());
        when(jwt.obtenerUsuarioId("old-t")).thenReturn(50L);
        when(jwt.obtenerRoles("old-t")).thenReturn(Set.of(Rol.STORE_ADMIN));
        when(otp.estaUsuarioRevocado(50L, 5000L)).thenReturn(false);
        when(jwt.obtenerTiempoRestanteSegundos("old-t")).thenReturn(120L);
        when(entidadPort.resolverEntidadAsociada(50L, Set.of(Rol.STORE_ADMIN))).thenReturn(EntidadAsociada.VACIA);
        when(jwt.generarToken(50L, Set.of(Rol.STORE_ADMIN), "900555666-1", null)).thenReturn("new-t");

        RenovarTokenUseCaseImpl refreshUseCase = new RenovarTokenUseCaseImpl(jwt, otp, null, entidadPort);
        SesionAutenticada sesionRefresh = refreshUseCase.renovar("old-t");
        assertThat(sesionRefresh.accessToken()).isEqualTo("new-t");
    }
}
