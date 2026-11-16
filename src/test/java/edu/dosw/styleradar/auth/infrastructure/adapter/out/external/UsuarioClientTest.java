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
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                            "id": 10,
                            "email": "comprador@styleradar.com",
                            "rol": "COMPRADOR",
                            "estado": "ACTIVA"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        VerificarCredencialesResponseDTO response =
                usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*");

        assertEquals(10L, response.id());
        assertEquals("comprador@styleradar.com", response.email());
        assertEquals("COMPRADOR", response.rol());
        assertEquals("ACTIVA", response.estado());
        server.verify();
    }

    @Test
    @DisplayName("Debe propagar HttpClientErrorException cuando ms-usuarios responde 401")
    void verificarCredenciales_conRespuesta401_debeLanzarHttpClientError() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(
                HttpClientErrorException.class,
                () -> usuarioClient.verificarCredenciales("comprador@styleradar.com", "Erronea123*")
        );
    }

    @Test
    @DisplayName("Debe fallar cerrado con 503 fuera de perfil dev cuando ms-usuarios no responde (OWASP A10:2025)")
    void verificarCredenciales_sinConexionPorDefecto_debeFallarCerrado() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
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
                        requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Sin conexión");
                });

        assertEquals("COMPRADOR",
                devClient.verificarCredenciales("comprador@styleradar.com", "Password123*").rol());
        assertEquals("ADMIN_STYLERADAR",
                devClient.verificarCredenciales("admin@styleradar.com", "Password123*").rol());
        assertEquals("ADMIN_ALMACEN",
                devClient.verificarCredenciales("almacen@styleradar.com", "Password123*").rol());
        assertEquals("REPRESENTANTE_FUNDACION",
                devClient.verificarCredenciales("fundacion@styleradar.com", "Password123*").rol());

        for (String badPassword : new String[]{"", "wrong", "incorrecta"}) {
            assertThrows(CredencialesIncorrectasException.class,
                    () -> devClient.verificarCredenciales("comprador@styleradar.com", badPassword));
        }
    }

    @Test
    @DisplayName("Debe resolver usuario OAuth2 cuando ms-usuarios responde 200 OK")
    void resolverUsuarioOAuth_conRespuestaExitosa_debeRetornarUsuario() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/oauth2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        """
                        {
                            "id": 25,
                            "email": "usuario@gmail.com",
                            "rol": "COMPRADOR",
                            "estado": "ACTIVA"
                        }
                        """,
                        MediaType.APPLICATION_JSON
                ));

        VerificarCredencialesResponseDTO response = usuarioClient.resolverUsuarioOAuth(
                "  Usuario@Gmail.com ", "Ana Perez", "GOOGLE", "sub-123");

        assertEquals(25L, response.id());
        assertEquals("usuario@gmail.com", response.email());
        assertEquals("COMPRADOR", response.rol());
        assertEquals("ACTIVA", response.estado());
        server.verify();
    }

    @Test
    @DisplayName("Debe mapear 401/403/5xx/timeout de ms-usuarios correctamente en resolverUsuarioOAuth")
    void resolverUsuarioOAuth_conErrores_debeMapearExcepcionesDeDominio() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/oauth2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThrows(CredencialesIncorrectasException.class,
                () -> usuarioClient.resolverUsuarioOAuth("u@gmail.com", "Ana", "GOOGLE", "1"));

        server.reset();
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/oauth2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException.class,
                () -> usuarioClient.resolverUsuarioOAuth("u@gmail.com", "Ana", "GOOGLE", "1"));

        server.reset();
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/oauth2"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(ServicioExternoNoDisponibleException.class,
                () -> usuarioClient.resolverUsuarioOAuth("u@gmail.com", "Ana", "GOOGLE", "1"));

        server.reset();
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/oauth2"))
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
                devBuilder, BASE_URL, "u", "p", true, devEnv, "/internal/api/v1/usuarios/oauth2");

        devServer.expect(ExpectedCount.once(), requestTo(BASE_URL + "/internal/api/v1/usuarios/oauth2"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Offline dev");
                });

        VerificarCredencialesResponseDTO response =
                devClient.resolverUsuarioOAuth("cliente.google@gmail.com", "Cliente", "GOOGLE", "sub-dev");

        assertEquals("ACTIVA", response.estado());
        assertEquals("COMPRADOR", response.rol());
        devServer.verify();
    }
}
