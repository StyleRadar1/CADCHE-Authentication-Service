package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;

@DisplayName("Pruebas Unitarias de UsuarioClient - OWASP A08 / A10:2025")
class UsuarioClientTest {

    private MockRestServiceServer server;
    private UsuarioClient usuarioClient;

    private static final String BASE_URL = "http://localhost:8082";

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        usuarioClient = new UsuarioClient(
                builder,
                BASE_URL,
                "test-user",
                "test-password"
        );
    }

    @Test
    @DisplayName("Debe verificar credenciales cuando ms-usuarios responde correctamente")
    void verificarCredenciales_conRespuestaExitosa_debeRetornarUsuario() {
        server.expect(requestTo(BASE_URL + "/api/v1/internal/users/verify-credentials"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                            "id": 10,
                            "email": "comprador@styleradar.com",
                            "rol": "BUYER",
                            "estado": "ACTIVA"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        VerificarCredencialesResponseDTO response =
                usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*");

        assertEquals(10L, response.id());
        assertEquals("comprador@styleradar.com", response.email());
        assertEquals("BUYER", response.rol());
        assertEquals("ACTIVA", response.estado());
        server.verify();
    }

    @Test
    @DisplayName("Debe traducir 401/403/5xx de ms-usuarios a excepciones del dominio (RN-04, RN-03, fail-closed)")
    void verificarCredenciales_conErroresHttp_debeTraducirAExcepcionesDeDominio() {
        String ruta = BASE_URL + "/api/v1/internal/users/verify-credentials";
        server.expect(requestTo(ruta)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(ruta)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(ruta)).andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(CredencialesIncorrectasException.class,
                () -> usuarioClient.verificarCredenciales("comprador@styleradar.com", "Erronea123*"));
        // 403 sin motivo de cuenta: es la seguridad de ms-usuarios rechazando a ms-auth, no la cuenta -> 503
        assertThrows(ServicioExternoNoDisponibleException.class,
                () -> usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*"));
        assertThrows(ServicioExternoNoDisponibleException.class,
                () -> usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*"));
        server.verify();
    }

    @Test
    @DisplayName("Debe marcar el correo como verificado con PATCH y tolerar la caída de ms-usuarios")
    void marcarCorreoVerificado_debeInvocarPatchYSerTolerante() {
        String ruta = BASE_URL + "/api/v1/internal/users/10/email-verification";
        server.expect(requestTo(ruta)).andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.NO_CONTENT));
        server.expect(requestTo(ruta)).andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        usuarioClient.marcarCorreoVerificado(10L);
        usuarioClient.marcarCorreoVerificado(10L);
        usuarioClient.marcarCorreoVerificado(null);
        server.verify();
    }

    @Test
    @DisplayName("Debe fallar cerrado con 503 fuera de perfil dev cuando ms-usuarios no responde (OWASP A10:2025)")
    void verificarCredenciales_sinConexionPorDefecto_debeFallarCerrado() {
        server.expect(requestTo(BASE_URL + "/api/v1/internal/users/verify-credentials"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Servicio no disponible");
                });

        assertThrows(
                ServicioExternoNoDisponibleException.class,
                () -> usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*")
        );
        server.verify();
    }

    @Test
    @DisplayName("Debe rechazar URLs peligrosas o de metadatos SSRF (OWASP A08:2025)")
    void constructor_conUrlInseguraOSsrf_debeRechazar() {
        RestClient.Builder builder = RestClient.builder();

        assertThrows(IllegalArgumentException.class,
                () -> new UsuarioClient(builder, "file:///etc/passwd", "u", "p"));
        assertThrows(IllegalArgumentException.class,
                () -> new UsuarioClient(builder, "http://169.254.169.254/latest/meta-data", "u", "p"));
        assertThrows(IllegalArgumentException.class,
                () -> new UsuarioClient(builder, "http://metadata.google.internal", "u", "p"));
        assertThrows(IllegalArgumentException.class,
                () -> new UsuarioClient(builder, "https://user:pass@ms-usuarios.internal", "u", "p"));

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        assertThrows(IllegalArgumentException.class,
                () -> new UsuarioClient(builder, "http://ms-usuarios-prod.internal", "u", "p", false, prodEnv));
    }

    @Test
    @DisplayName("Debe usar fallback dev explícito cuando se habilita exclusivamente en desarrollo")
    void verificarCredenciales_conFallbackDevExplicito_debeAsignarRolesYValidarClave() {
        RestClient.Builder devBuilder = RestClient.builder();
        MockRestServiceServer devServer = MockRestServiceServer.bindTo(devBuilder).build();
        UsuarioClient devClient = new UsuarioClient(devBuilder, BASE_URL, "u", "p", true);

        devServer.expect(ExpectedCount.manyTimes(),
                        requestTo(BASE_URL + "/api/v1/internal/users/verify-credentials"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Sin conexión");
                });

        assertEquals("BUYER",
                devClient.verificarCredenciales("comprador@styleradar.com", "Password123*").rol());
        assertEquals("STYLERADAR_ADMIN",
                devClient.verificarCredenciales("admin@styleradar.com", "Password123*").rol());
        assertEquals("STORE_ADMIN",
                devClient.verificarCredenciales("almacen@styleradar.com", "Password123*").rol());
        assertEquals("FOUNDATION_REPRESENTATIVE",
                devClient.verificarCredenciales("fundacion@styleradar.com", "Password123*").rol());

        for (String badPassword : new String[]{"", "wrong", "incorrecta"}) {
            assertThrows(CredencialesIncorrectasException.class,
                    () -> devClient.verificarCredenciales("comprador@styleradar.com", badPassword));
        }
    }

    @Test
    @DisplayName("Debe resolver usuario OAuth2 cuando ms-usuarios responde 200 OK")
    void resolverUsuarioOAuth_conRespuestaExitosa_debeRetornarUsuario() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/users/oauth2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                            "id": 25,
                            "email": "usuario@gmail.com",
                            "rol": "BUYER",
                            "estado": "ACTIVA"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        VerificarCredencialesResponseDTO response = usuarioClient.resolverUsuarioOAuth(
                "  Usuario@Gmail.com ", "Ana Perez", "GOOGLE", "sub-123");

        assertEquals(25L, response.id());
        assertEquals("usuario@gmail.com", response.email());
        assertEquals("BUYER", response.rol());
        assertEquals("ACTIVA", response.estado());
        server.verify();
    }

    @Test
    @DisplayName("Debe mapear 401/403/5xx/timeout de ms-usuarios correctamente en resolverUsuarioOAuth")
    void resolverUsuarioOAuth_conErrores_debeMapearExcepcionesDeDominio() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/users/oauth2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(CredencialesIncorrectasException.class,
                () -> usuarioClient.resolverUsuarioOAuth("u@gmail.com", "Ana", "GOOGLE", "1"));

        server.reset();
        server.expect(requestTo(BASE_URL + "/internal/api/v1/users/oauth2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException.class,
                () -> usuarioClient.resolverUsuarioOAuth("u@gmail.com", "Ana", "GOOGLE", "1"));

        server.reset();
        server.expect(requestTo(BASE_URL + "/internal/api/v1/users/oauth2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(ServicioExternoNoDisponibleException.class,
                () -> usuarioClient.resolverUsuarioOAuth("u@gmail.com", "Ana", "GOOGLE", "1"));

        server.reset();
        server.expect(requestTo(BASE_URL + "/internal/api/v1/users/oauth2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Timeout");
                });

        assertThrows(ServicioExternoNoDisponibleException.class,
                () -> usuarioClient.resolverUsuarioOAuth("u@gmail.com", "Ana", "GOOGLE", "1"));
    }

    @Test
    @DisplayName("Debe usar fallback dev en resolverUsuarioOAuth únicamente en perfil dev")
    void resolverUsuarioOAuth_conFallbackDev_debeRetornarUsuarioActivo() {
        RestClient.Builder devBuilder = RestClient.builder();
        MockRestServiceServer devServer = MockRestServiceServer.bindTo(devBuilder).build();
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        UsuarioClient devClient = new UsuarioClient(
                devBuilder, BASE_URL, "u", "p", true, devEnv, "/internal/api/v1/users/oauth2",
                "/api/v1/internal/users/{id}/email-verification", "/api/v1/internal/users/verify-credentials");

        devServer.expect(ExpectedCount.once(), requestTo(BASE_URL + "/internal/api/v1/users/oauth2"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Offline dev");
                });

        VerificarCredencialesResponseDTO response =
                devClient.resolverUsuarioOAuth("cliente.google@gmail.com", "Cliente", "GOOGLE", "sub-dev");

        assertEquals("ACTIVA", response.estado());
        assertEquals("BUYER", response.rol());
        devServer.verify();
    }

    @Test
    @DisplayName("Si ms-usuarios aún no expone las rutas en inglés (404), usa la ruta anterior y conserva los errores 401/403")
    void rutasEnIngles_conRespaldoALaRutaAnterior() {
        org.springframework.test.util.ReflectionTestUtils.setField(usuarioClient, "credencialesLegacyPath",
                "/internal/api/v1/usuarios/verificar-credenciales");
        org.springframework.test.util.ReflectionTestUtils.setField(usuarioClient, "correoVerificadoLegacyPath",
                "/api/v1/internal/usuarios/{id}/correo-verificado");

        server.expect(requestTo(BASE_URL + "/api/v1/internal/users/verify-credentials"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andRespond(withSuccess("{\"id\":10,\"email\":\"a@b.co\",\"rol\":\"COMPRADOR\",\"estado\":\"ACTIVO\",\"correoVerificado\":true}",
                        org.springframework.http.MediaType.APPLICATION_JSON));

        server.expect(requestTo(BASE_URL + "/api/v1/internal/users/verify-credentials"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        server.expect(requestTo(BASE_URL + "/api/v1/internal/users/10/email-verification"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE_URL + "/api/v1/internal/usuarios/10/correo-verificado"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(org.springframework.http.HttpStatus.NO_CONTENT));

        var r = usuarioClient.verificarCredenciales("a@b.co", "StyleRadar2026*");
        org.assertj.core.api.Assertions.assertThat(r.rol()).isEqualTo("COMPRADOR");
        org.assertj.core.api.Assertions.assertThat(r.correoVerificado()).isTrue();
        assertThrows(ServicioExternoNoDisponibleException.class,
                () -> usuarioClient.verificarCredenciales("a@b.co", "StyleRadar2026*"));
        usuarioClient.marcarCorreoVerificado(10L);
        server.verify();
    }

    @Test
    @DisplayName("Acepta la respuesta de ms-usuarios con los nombres del análisis v2 (role, status, emailVerified)")
    void respuestaV2DeMsUsuarios() {
        server.expect(requestTo(BASE_URL + "/api/v1/internal/users/verify-credentials"))
                .andRespond(withSuccess("{\"id\":7,\"email\":\"s@b.co\",\"roles\":[\"STORE_ADMIN\"],\"accountStatus\":\"ACTIVE\",\"emailVerified\":false}",
                        org.springframework.http.MediaType.APPLICATION_JSON));
        var r = usuarioClient.verificarCredenciales("s@b.co", "StyleRadar2026*");
        org.assertj.core.api.Assertions.assertThat(r.rol()).isEqualTo("STORE_ADMIN");
        org.assertj.core.api.Assertions.assertThat(r.estado()).isEqualTo("ACTIVE");
        org.assertj.core.api.Assertions.assertThat(r.correoVerificado()).isFalse();
    }

    @Test
    @DisplayName("Si ms-usuarios responde 403 denyAll() en ruta en inglés, usa la ruta anterior y respeta 403 real de cuenta inactiva")
    void rutasEnIngles_con403DeDenyAllEnMsUsuarios_usaRutaAnteriorYConservaCuentaInactiva() {
        org.springframework.test.util.ReflectionTestUtils.setField(usuarioClient, "credencialesLegacyPath",
                "/internal/api/v1/usuarios/verificar-credenciales");
        org.springframework.test.util.ReflectionTestUtils.setField(usuarioClient, "correoVerificadoLegacyPath",
                "/api/v1/internal/usuarios/{id}/correo-verificado");

        // 1) 403 vacío de Spring Security denyAll() -> respalda en ruta legacy -> 200 OK
        server.expect(requestTo(BASE_URL + "/api/v1/internal/users/verify-credentials"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"id\":2,\"email\":\"thedrup321@gmail.com\",\"rol\":\"COMPRADOR\",\"estado\":\"ACTIVA\",\"correoVerificado\":false}",
                        MediaType.APPLICATION_JSON));

        // 2) 403 real con cuerpo JSON de cuenta inactiva -> NO respalda y lanza CuentaNoActivaException directo
        server.expect(requestTo(BASE_URL + "/api/v1/internal/users/verify-credentials"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"La cuenta se encuentra inactiva o bloqueada\"}"));

        // 3) PATCH correo-verificado con 403 denyAll() -> respalda en ruta legacy -> 204 No Content
        server.expect(requestTo(BASE_URL + "/api/v1/internal/users/10/email-verification"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(BASE_URL + "/api/v1/internal/usuarios/10/correo-verificado"))
                .andExpect(method(HttpMethod.PATCH))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        var ok = usuarioClient.verificarCredenciales("thedrup321@gmail.com", "StyleRadar2026*");
        assertEquals(2L, ok.id());
        assertEquals("COMPRADOR", ok.rol());

        assertThrows(CuentaNoActivaException.class,
                () -> usuarioClient.verificarCredenciales("inactiva@styleradar.com", "StyleRadar2026*"));

        usuarioClient.marcarCorreoVerificado(10L);
        server.verify();
    }

    @Test
    @DisplayName("Debe detectar todas las variantes de 403 de cuenta no activa y respaldar 403 JSON ajeno o 404 en respaldo")
    void rutasEnIngles_variantes403YRespaldoInexistente() {
        org.springframework.test.util.ReflectionTestUtils.setField(usuarioClient, "credencialesLegacyPath",
                "/internal/api/v1/usuarios/verificar-credenciales");

        String rutaIngles = BASE_URL + "/api/v1/internal/users/verify-credentials";
        String rutaLegacy = BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales";

        // Variantes de cuerpo 403 que indican cuenta inactiva/bloqueada -> NO respaldan
        for (String body : new String[]{
                "{\"code\":\"ACCOUNT_NOT_ACTIVE\"}",
                "{\"detail\":\"Cuenta bloqueada por administrador\"}",
                "{\"detail\":\"User account is inactive\"}",
                "{\"detail\":\"User account is blocked\"}"
        }) {
            server.expect(requestTo(rutaIngles))
                    .andExpect(method(HttpMethod.POST))
                    .andRespond(withStatus(HttpStatus.FORBIDDEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body));
        }

        // 403 con JSON genérico (sin palabras de cuenta inactiva) -> SÍ respalda en ruta legacy
        server.expect(requestTo(rutaIngles))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Forbidden\",\"message\":\"Access Denied\"}"));
        server.expect(requestTo(rutaLegacy))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"id\":3,\"email\":\"ok@styleradar.com\",\"rol\":\"COMPRADOR\",\"estado\":\"ACTIVA\",\"correoVerificado\":true}",
                        MediaType.APPLICATION_JSON));

        // 403 en ruta inglés y 404 en ruta legacy -> relanza falloRuta (403 sin motivo de cuenta -> 503)
        server.expect(requestTo(rutaIngles))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(rutaLegacy))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        for (int i = 0; i < 4; i++) {
            assertThrows(CuentaNoActivaException.class,
                    () -> usuarioClient.verificarCredenciales("bloqueada@styleradar.com", "StyleRadar2026*"));
        }

        var ok = usuarioClient.verificarCredenciales("ok@styleradar.com", "StyleRadar2026*");
        assertEquals(3L, ok.id());

        assertThrows(ServicioExternoNoDisponibleException.class,
                () -> usuarioClient.verificarCredenciales("sinrespaldo@styleradar.com", "StyleRadar2026*"));

        server.verify();
    }

    @Test
    @DisplayName("Si la ruta legacy es vacía o igual a la principal, no reintenta y tolera 404/403 en marcarCorreoVerificado")
    void marcarCorreoVerificado_sinRespaldoConfigurado_tolera404Y403() {
        String rutaIngles = BASE_URL + "/api/v1/internal/users/10/email-verification";

        server.expect(requestTo(rutaIngles)).andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(rutaIngles)).andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.FORBIDDEN));
        server.expect(requestTo(rutaIngles)).andExpect(method(HttpMethod.PATCH)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        org.springframework.test.util.ReflectionTestUtils.setField(usuarioClient, "correoVerificadoLegacyPath", null);
        usuarioClient.marcarCorreoVerificado(10L);

        org.springframework.test.util.ReflectionTestUtils.setField(usuarioClient, "correoVerificadoLegacyPath", "   ");
        usuarioClient.marcarCorreoVerificado(10L);

        org.springframework.test.util.ReflectionTestUtils.setField(usuarioClient, "correoVerificadoLegacyPath",
                UsuarioClient.DEFAULT_CORREO_VERIFICADO_PATH);
        usuarioClient.marcarCorreoVerificado(10L);

        server.verify();
    }
}
