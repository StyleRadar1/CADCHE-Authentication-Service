package edu.dosw.styleradar.auth.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;

import edu.dosw.styleradar.auth.application.dto.response.GoogleAuthUrlResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.application.service.impl.GoogleOAuth2ServiceImpl;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.GoogleIdentityClaims;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.domain.model.SesionAutenticada;
import edu.dosw.styleradar.auth.domain.port.out.GoogleOAuthPort;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioIdentidadAdapter;

@DisplayName("Pruebas Unitarias de GoogleOAuth2ServiceImpl")
class GoogleOAuth2ServiceTest {

    private static final String TEST_CLIENT_ID = "123456-styleradar-test.apps.googleusercontent.com";
    private static final String DEFAULT_REDIRECT = "http://localhost:8100/api/v1/auth/oauth2/google/callback";
    private static final String FRONTEND_REDIRECT = "https://styleradar.com/auth/google/callback";
    private static final String ALLOWED_REDIRECTS = DEFAULT_REDIRECT + "," + FRONTEND_REDIRECT;

    private GoogleOAuthPort googleOAuthPort;
    private UsuarioClient usuarioClient;
    private JwtService jwtService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private GoogleOAuth2ServiceImpl service;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        googleOAuthPort = mock(GoogleOAuthPort.class);
        usuarioClient = mock(UsuarioClient.class);
        jwtService = mock(JwtService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtService.obtenerVigenciaSegundos()).thenReturn(86400L);

        service = new GoogleOAuth2ServiceImpl(
                googleOAuthPort,
                new UsuarioIdentidadAdapter(usuarioClient),
                jwtService,
                redisTemplate,
                TEST_CLIENT_ID,
                DEFAULT_REDIRECT,
                ALLOWED_REDIRECTS,
                300L,
                false);
    }

    @Test
    @DisplayName("Debe generar URL de autorización Google con state criptográfico almacenado en Redis")
    void generarUrlAutorizacion_conConfiguracionValida_debeGuardarStateYRetornarUrl() {
        GoogleAuthUrlResponseDTO urlDto = service.generarUrlAutorizacion(null);

        assertNotNull(urlDto.authorizationUrl());
        assertTrue(urlDto.authorizationUrl().startsWith("https://accounts.google.com/o/oauth2/v2/auth"));
        assertTrue(urlDto.authorizationUrl().contains("client_id=" + TEST_CLIENT_ID));
        assertTrue(urlDto.authorizationUrl().contains("response_type=code"));
        assertTrue(urlDto.authorizationUrl().contains("state=" + urlDto.state()));
        assertEquals(300L, urlDto.expiresInSeconds());
        assertEquals(DEFAULT_REDIRECT, urlDto.redirectUri());

        verify(valueOperations).set(
                eq("oauth2:state:" + urlDto.state()),
                eq(DEFAULT_REDIRECT),
                eq(300L),
                eq(TimeUnit.SECONDS));

        GoogleAuthUrlResponseDTO segundaUrl = service.generarUrlAutorizacion(FRONTEND_REDIRECT);
        assertNotEquals(urlDto.state(), segundaUrl.state());
        assertEquals(FRONTEND_REDIRECT, segundaUrl.redirectUri());
    }

    @Test
    @DisplayName("Debe rechazar redirectUri fuera de lista blanca para prevenir Open Redirect (OWASP A01:2025)")
    void generarUrlAutorizacion_conRedirectUriNoAutorizada_debeLanzarIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.generarUrlAutorizacion("https://malicioso.ejemplo.com/callback"));

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("El inicio de sesión con Google abre la sesión para user.session.changed")
    void autenticarConIdToken_registraLaSesion() {
        GoogleIdentityClaims claims = new GoogleIdentityClaims("google-sub-109", "antonia@styleradar.com", true, "Antonia");
        when(googleOAuthPort.verificarIdToken("id-token-valido")).thenReturn(claims);
        when(usuarioClient.resolverUsuarioOAuth("antonia@styleradar.com", "Antonia", "GOOGLE", "google-sub-109"))
                .thenReturn(new VerificarCredencialesResponseDTO(42L, "antonia@styleradar.com", "BUYER", "ACTIVA"));
        when(jwtService.generarToken(42L, Set.of(Rol.BUYER))).thenReturn("jwt-styleradar-42");
        edu.dosw.styleradar.auth.application.service.SesionesActivasService sesiones =
                org.mockito.Mockito.mock(edu.dosw.styleradar.auth.application.service.SesionesActivasService.class);
        service.setSesiones(sesiones);

        service.autenticarConIdToken("id-token-valido");

        verify(sesiones).iniciar("jwt-styleradar-42");
    }

    @Test
    @DisplayName("Debe autenticar exitosamente con ID Token verificado y emitir JWT Bearer de StyleRadar")
    void autenticarConIdToken_conTokenYCuentaActiva_debeRetornarJwtSinOtp() {
        GoogleIdentityClaims claims = new GoogleIdentityClaims(
                "google-sub-109",
                "Antonia@StyleRadar.com",
                true,
                "Antonia Sastre");

        when(googleOAuthPort.verificarIdToken("id-token-valido")).thenReturn(claims);
        when(usuarioClient.resolverUsuarioOAuth(
                "antonia@styleradar.com", "Antonia Sastre", "GOOGLE", "google-sub-109"))
                .thenReturn(new VerificarCredencialesResponseDTO(42L, "antonia@styleradar.com", "BUYER", "ACTIVA"));
        when(jwtService.generarToken(42L, Set.of(Rol.BUYER))).thenReturn("jwt-styleradar-42");

        SesionAutenticada response = service.autenticarConIdToken("id-token-valido");

        assertEquals("jwt-styleradar-42", response.accessToken());
        assertEquals(86400L, response.expiraEnSegundos());
        assertEquals(42L, response.usuarioId());
        assertEquals(Set.of(Rol.BUYER), Set.of(response.rol()));
        assertFalse(response.requiereOtp());
        assertTrue(response.correoVerificado());
        assertEquals("antonia@styleradar.com", response.email());
        verify(usuarioClient).marcarCorreoVerificado(42L);
    }

    @Test
    @DisplayName("Debe rechazar ID Token de Google cuyo correo no está verificado")
    void autenticarConIdToken_conEmailNoVerificado_debeLanzarCredencialesIncorrectas() {
        when(googleOAuthPort.verificarIdToken("id-token-no-verificado"))
                .thenReturn(new GoogleIdentityClaims("sub-1", "no.verificado@gmail.com", false, "Sin Verificar"));

        assertThrows(
                CredencialesIncorrectasException.class,
                () -> service.autenticarConIdToken("id-token-no-verificado"));

        verify(usuarioClient, never()).resolverUsuarioOAuth(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Debe rechazar acceso Google OAuth2 si la cuenta en StyleRadar está INACTIVA o BLOQUEADA (403)")
    void autenticarConIdToken_conCuentaBloqueada_debeLanzarCuentaNoActiva() {
        when(googleOAuthPort.verificarIdToken("id-token-valido"))
                .thenReturn(new GoogleIdentityClaims("sub-2", "bloqueado@gmail.com", true, "Usuario Bloqueado"));
        when(usuarioClient.resolverUsuarioOAuth("bloqueado@gmail.com", "Usuario Bloqueado", "GOOGLE", "sub-2"))
                .thenReturn(new VerificarCredencialesResponseDTO(77L, "bloqueado@gmail.com", "BUYER", "BLOQUEADA"));

        assertThrows(
                CuentaNoActivaException.class,
                () -> service.autenticarConIdToken("id-token-valido"));

        verify(jwtService, never()).generarToken(anyLong(), anySet());
    }

    @Test
    @DisplayName("Debe intercambiar Authorization Code consumiendo state de un solo uso y emitir JWT")
    void autenticarConCodigo_conStateValidoYUnSoloUso_debeEmitirJwt() {
        when(valueOperations.getAndDelete("oauth2:state:state-123")).thenReturn(DEFAULT_REDIRECT);
        when(googleOAuthPort.intercambiarCodigoPorIdToken("auth-code-999", DEFAULT_REDIRECT))
                .thenReturn("google-id-token-intercambiado");
        when(googleOAuthPort.verificarIdToken("google-id-token-intercambiado"))
                .thenReturn(new GoogleIdentityClaims("sub-99", "admin.tienda@gmail.com", true, "Admin Tienda"));
        when(usuarioClient.resolverUsuarioOAuth("admin.tienda@gmail.com", "Admin Tienda", "GOOGLE", "sub-99"))
                .thenReturn(new VerificarCredencialesResponseDTO(
                        88L, "admin.tienda@gmail.com", "STORE_ADMIN", "ACTIVA"));
        when(jwtService.generarToken(88L, Set.of(Rol.STORE_ADMIN))).thenReturn("jwt-almacen-88");

        SesionAutenticada response = service.autenticarConCodigo("auth-code-999", "state-123", null);

        assertEquals("jwt-almacen-88", response.accessToken());
        assertEquals(Set.of(Rol.STORE_ADMIN), Set.of(response.rol()));
        assertFalse(response.requiereOtp());
        verify(valueOperations).getAndDelete("oauth2:state:state-123");
    }

    @Test
    @DisplayName("Debe rechazar state repetido, expirado o con mismatch de redirectUri (anti-CSRF)")
    void autenticarConCodigo_conStateInvalidoOMismatch_debeRechazar() {
        when(valueOperations.getAndDelete("oauth2:state:replayed")).thenReturn(null);

        assertThrows(
                CredencialesIncorrectasException.class,
                () -> service.autenticarConCodigo("code-1", "replayed", null));

        when(valueOperations.getAndDelete("oauth2:state:state-abc")).thenReturn(DEFAULT_REDIRECT);
        assertThrows(
                CredencialesIncorrectasException.class,
                () -> service.autenticarConCodigo("code-1", "state-abc", FRONTEND_REDIRECT));

        verify(googleOAuthPort, never()).intercambiarCodigoPorIdToken(anyString(), anyString());
    }

    @Test
    @DisplayName("Debe fallar cerrado (503) cuando Redis cae fuera de perfil dev o GOOGLE_CLIENT_ID no está configurado")
    void operaciones_cuandoFallaRedisOFaltaClientId_debenFallarCerrado() {
        when(valueOperations.getAndDelete(anyString())).thenThrow(new RedisConnectionFailureException("Redis caído"));
        assertThrows(
                ServicioExternoNoDisponibleException.class,
                () -> service.autenticarConCodigo("code-1", "state-ok", null));

        GoogleOAuth2ServiceImpl unconfigured = new GoogleOAuth2ServiceImpl(
                googleOAuthPort,
                new UsuarioIdentidadAdapter(usuarioClient),
                jwtService,
                redisTemplate,
                "",
                DEFAULT_REDIRECT,
                ALLOWED_REDIRECTS,
                300L,
                false);

        assertThrows(ServicioExternoNoDisponibleException.class, () -> unconfigured.generarUrlAutorizacion(null));
        assertThrows(ServicioExternoNoDisponibleException.class, () -> unconfigured.autenticarConIdToken("token"));
    }

    @Test
    @DisplayName("Debe permitir fallback local en memoria para state OAuth2 únicamente en perfil dev")
    void generarYConsumirState_conFallbackDev_debeFuncionarYSerUnSoloUso() {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        when(valueOperations.getAndDelete(anyString())).thenThrow(new RedisConnectionFailureException("Redis dev offline"));
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("Redis dev offline"))
                .when(valueOperations).set(anyString(), anyString(), anyLong(), any());

        GoogleOAuth2ServiceImpl devService = new GoogleOAuth2ServiceImpl(
                googleOAuthPort,
                new UsuarioIdentidadAdapter(usuarioClient),
                jwtService,
                redisTemplate,
                devEnv,
                TEST_CLIENT_ID,
                DEFAULT_REDIRECT,
                ALLOWED_REDIRECTS,
                "https://accounts.google.com/o/oauth2/v2/auth",
                300L,
                true);

        when(jwtService.obtenerVigenciaSegundos()).thenReturn(43200L);
        GoogleAuthUrlResponseDTO generated = devService.generarUrlAutorizacion(null);
        when(googleOAuthPort.intercambiarCodigoPorIdToken("code-dev", DEFAULT_REDIRECT)).thenReturn("id-dev");
        when(googleOAuthPort.verificarIdToken("id-dev"))
                .thenReturn(new GoogleIdentityClaims("sub-dev", "dev@gmail.com", true, "Dev"));
        when(usuarioClient.resolverUsuarioOAuth("dev@gmail.com", "Dev", "GOOGLE", "sub-dev"))
                .thenReturn(new VerificarCredencialesResponseDTO(5L, "dev@gmail.com", "BUYER", "ACTIVA"));
        when(jwtService.generarToken(5L, Set.of(Rol.BUYER))).thenReturn("jwt-dev");

        SesionAutenticada firstAttempt = devService.autenticarConCodigo("code-dev", generated.state(), null);
        assertEquals(43200L, firstAttempt.expiraEnSegundos());

        String usedState = generated.state();
        assertThrows(
                CredencialesIncorrectasException.class,
                () -> devService.autenticarConCodigo("code-dev", usedState, null));
    }

    @Test
    @DisplayName("B8: si la cuenta vinculada con Google ya tiene el correo verificado no se vuelve a marcar en ms-usuarios")
    void autenticarConIdToken_conCorreoYaVerificado_noDebeMarcarDeNuevo() {
        GoogleIdentityClaims claims = new GoogleIdentityClaims("sub-7", "ana@gmail.com", true, "Ana");
        when(googleOAuthPort.verificarIdToken("id-ok")).thenReturn(claims);
        when(usuarioClient.resolverUsuarioOAuth("ana@gmail.com", "Ana", "GOOGLE", "sub-7"))
                .thenReturn(new VerificarCredencialesResponseDTO(7L, "ana@gmail.com", "BUYER", "ACTIVA", true));
        when(jwtService.generarToken(7L, Set.of(Rol.BUYER))).thenReturn("jwt-7");

        SesionAutenticada sesion = service.autenticarConIdToken("id-ok");

        assertEquals("jwt-7", sesion.accessToken());
        assertFalse(sesion.requiereOtp());
        verify(usuarioClient, never()).marcarCorreoVerificado(anyLong());
    }
}
