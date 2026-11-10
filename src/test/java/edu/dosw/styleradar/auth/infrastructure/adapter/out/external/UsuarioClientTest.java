package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;

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
                usuarioClient.verificarCredenciales(
                        "comprador@styleradar.com",
                        "Password123*"
                );

        assertEquals(10L, response.id());
        assertEquals("comprador@styleradar.com", response.email());
        assertEquals("COMPRADOR", response.rol());
        assertEquals("ACTIVA", response.estado());

        server.verify();
    }

    @Test
    @DisplayName("Debe utilizar fallback local cuando no hay conexión")
    void verificarCredenciales_sinConexion_debeUsarFallbackLocal() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Servicio no disponible");
                });

        VerificarCredencialesResponseDTO response =
                usuarioClient.verificarCredenciales(
                        "comprador@styleradar.com",
                        "Password123*"
                );

        assertEquals(10L, response.id());
        assertEquals("comprador@styleradar.com", response.email());
        assertEquals("COMPRADOR", response.rol());
        assertEquals("ACTIVA", response.estado());

        server.verify();
    }

    @Test
    @DisplayName("Debe asignar rol ADMIN_STYLERADAR en fallback")
    void verificarCredenciales_conCorreoAdmin_debeAsignarRolAdmin() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Sin conexión");
                });

        VerificarCredencialesResponseDTO response =
                usuarioClient.verificarCredenciales(
                        "admin@styleradar.com",
                        "Password123*"
                );

        assertEquals("ADMIN_STYLERADAR", response.rol());
    }

    @Test
    @DisplayName("Debe asignar rol ADMIN_ALMACEN en fallback")
    void verificarCredenciales_conCorreoAlmacen_debeAsignarRolAlmacen() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Sin conexión");
                });

        VerificarCredencialesResponseDTO response =
                usuarioClient.verificarCredenciales(
                        "almacen@styleradar.com",
                        "Password123*"
                );

        assertEquals("ADMIN_ALMACEN", response.rol());
    }

    @Test
    @DisplayName("Debe asignar rol REPRESENTANTE_FUNDACION en fallback")
    void verificarCredenciales_conCorreoFundacion_debeAsignarRolFundacion() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Sin conexión");
                });

        VerificarCredencialesResponseDTO response =
                usuarioClient.verificarCredenciales(
                        "fundacion@styleradar.com",
                        "Password123*"
                );

        assertEquals("REPRESENTANTE_FUNDACION", response.rol());
    }

    @Test
    @DisplayName("Debe rechazar contraseña vacía en fallback")
    void verificarCredenciales_conPasswordVacio_debeLanzarExcepcion() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Sin conexión");
                });

        assertThrows(
                CredencialesIncorrectasException.class,
                () -> usuarioClient.verificarCredenciales(
                        "comprador@styleradar.com",
                        ""
                )
        );
    }

    @Test
    @DisplayName("Debe rechazar contraseña wrong en fallback")
    void verificarCredenciales_conPasswordWrong_debeLanzarExcepcion() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Sin conexión");
                });

        assertThrows(
                CredencialesIncorrectasException.class,
                () -> usuarioClient.verificarCredenciales(
                        "comprador@styleradar.com",
                        "wrong"
                )
        );
    }

    @Test
    @DisplayName("Debe rechazar contraseña incorrecta en fallback")
    void verificarCredenciales_conPasswordIncorrecta_debeLanzarExcepcion() {
        server.expect(requestTo(BASE_URL + "/internal/api/v1/usuarios/verificar-credenciales"))
                .andRespond(request -> {
                    throw new ResourceAccessException("Sin conexión");
                });

        assertThrows(
                CredencialesIncorrectasException.class,
                () -> usuarioClient.verificarCredenciales(
                        "comprador@styleradar.com",
                        "incorrecta"
                )
        );
    }
}

