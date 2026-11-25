package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;
import edu.dosw.styleradar.auth.domain.model.Rol;

@DisplayName("UsuarioClient: búsqueda por correo (U6) y cambio de contraseña (U7) en ms-usuarios")
class UsuarioClientRestablecimientoTest {

    private static final String BASE = "http://localhost:8120";

    private MockRestServiceServer server;
    private UsuarioClient client;

    @BeforeEach
    void preparar() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new UsuarioClient(builder, BASE, "ms-auth", "clave-interna");
    }

    @Test
    void buscarPorCorreoEnviaElCorreoEnElCuerpoYConvierteLaCuenta() {
        server.expect(requestTo(BASE + "/api/v1/internal/users/lookup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, org.hamcrest.Matchers.startsWith("Basic ")))
                .andExpect(content().json("{\"email\":\"tienda@almacenaliado.co\"}"))
                .andRespond(withSuccess("{\"id\":13,\"email\":\"tienda@almacenaliado.co\",\"role\":\"STORE_ADMIN\","
                        + "\"status\":\"ACTIVE\",\"emailVerified\":false}", MediaType.APPLICATION_JSON));

        var cuenta = new UsuarioIdentidadAdapter(client).buscarPorCorreo(" Tienda@AlmacenAliado.co ");

        assertThat(cuenta).hasValueSatisfying(c -> {
            assertThat(c.id()).isEqualTo(13L);
            assertThat(c.rol()).isEqualTo(Rol.STORE_ADMIN);
            assertThat(c.estado()).isEqualTo(EstadoCuenta.ACTIVA);
        });
        server.verify();
    }

    @Test
    void buscarPorCorreoInexistenteDevuelveVacioYErrorEs503() {
        server.expect(requestTo(BASE + "/api/v1/internal/users/lookup")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE + "/api/v1/internal/users/lookup")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThat(client.buscarPorCorreo("nadie@styleradar.com")).isEmpty();
        assertThatThrownBy(() -> client.buscarPorCorreo("nadie@styleradar.com"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    void cambiarContrasenaUsaPutConLaNuevaContrasena() {
        server.expect(requestTo(BASE + "/api/v1/internal/users/13/password"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"newPassword\":\"NuevaClave2026*\"}"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        new UsuarioIdentidadAdapter(client).cambiarContrasena(13L, "NuevaClave2026*");
        server.verify();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"FORBIDDEN", "NOT_FOUND"})
    void cuentaNoActivaOInexistenteEs403(HttpStatus estado) {
        server.expect(requestTo(BASE + "/api/v1/internal/users/13/password")).andRespond(withStatus(estado));
        assertThatThrownBy(() -> client.cambiarContrasena(13L, "NuevaClave2026*"))
                .isInstanceOf(CuentaNoActivaException.class);
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"BAD_REQUEST", "UNAUTHORIZED", "INTERNAL_SERVER_ERROR"})
    void otrosErroresSon503(HttpStatus estado) {
        server.expect(requestTo(BASE + "/api/v1/internal/users/13/password")).andRespond(withStatus(estado));
        assertThatThrownBy(() -> client.cambiarContrasena(13L, "NuevaClave2026*"))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    void rutasConfigurablesYVaciasUsanLasPorDefecto() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer otro = MockRestServiceServer.bindTo(builder).build();
        UsuarioClient conRutas = new UsuarioClient(builder, BASE, "ms-auth", "x", false, null,
                null, null, null, "/api/v1/internal/accounts/search", " ");
        otro.expect(requestTo(BASE + "/api/v1/internal/accounts/search")).andRespond(withStatus(HttpStatus.NOT_FOUND));
        otro.expect(requestTo(BASE + "/api/v1/internal/users/13/password")).andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThat(conRutas.buscarPorCorreo("a@b.co")).isEmpty();
        conRutas.cambiarContrasena(13L, "NuevaClave2026*");
        otro.verify();
    }
}
