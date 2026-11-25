package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import edu.dosw.styleradar.auth.domain.model.EntidadAsociada;
import edu.dosw.styleradar.auth.domain.model.Rol;

@DisplayName("Pruebas Unitarias de TiendasEntidadAdapter - BUG-005 fail-open")
class TiendasEntidadAdapterTest {

    private static final String BASE_URL = "http://localhost:8140";

    private static String basic(String user, String pass) {
        return "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + pass).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("STORE_ADMIN consulta /api/v1/internal/stores?ownerUserId={id} y extrae nit")
    void storeAdminExtraeStoreId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TiendasEntidadAdapter adapter = new TiendasEntidadAdapter(builder, BASE_URL, "ms-auth", "secret");

        server.expect(requestTo(BASE_URL + "/api/v1/internal/stores?ownerUserId=15"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", basic("ms-auth", "secret")))
                .andRespond(withSuccess("""
                        {"nit": "900123456-1", "commercialName": "Tienda Centro"}
                        """, MediaType.APPLICATION_JSON));

        EntidadAsociada entidad = adapter.resolverEntidadAsociada(15L, Set.of(Rol.STORE_ADMIN));

        assertThat(entidad.storeId()).isEqualTo("900123456-1");
        assertThat(entidad.foundationId()).isNull();
        server.verify();
    }

    @Test
    @DisplayName("FOUNDATION_REPRESENTATIVE consulta /api/v1/internal/foundations?representativeId={id} y extrae id")
    void foundationRepExtraeFoundationId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TiendasEntidadAdapter adapter = new TiendasEntidadAdapter(builder, BASE_URL, "ms-auth", "secret");

        server.expect(requestTo(BASE_URL + "/api/v1/internal/foundations?representativeId=24"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", basic("ms-auth", "secret")))
                .andRespond(withSuccess("""
                        {"id": 7, "name": "Fundación Abrigo"}
                        """, MediaType.APPLICATION_JSON));

        EntidadAsociada entidad = adapter.resolverEntidadAsociada(24L, Set.of(Rol.FOUNDATION_REPRESENTATIVE));

        assertThat(entidad.storeId()).isNull();
        assertThat(entidad.foundationId()).isEqualTo(7L);
        server.verify();
    }

    @Test
    @DisplayName("BUYER y STYLERADAR_ADMIN no realizan consultas HTTP a ms-tiendas")
    void otrosRolesNoConsultanMsTiendas() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TiendasEntidadAdapter adapter = new TiendasEntidadAdapter(builder, BASE_URL, "ms-auth", "secret");

        EntidadAsociada buyer = adapter.resolverEntidadAsociada(10L, Set.of(Rol.BUYER));
        EntidadAsociada admin = adapter.resolverEntidadAsociada(11L, Set.of(Rol.STYLERADAR_ADMIN));

        assertThat(buyer.estaVacia()).isTrue();
        assertThat(admin.estaVacia()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Fail-open: ante 404/401/503 de ms-tiendas devuelve vacío sin lanzar excepción")
    void erroresHttpSonFailOpen() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TiendasEntidadAdapter adapter = new TiendasEntidadAdapter(builder, BASE_URL, "ms-auth", "secret");

        server.expect(requestTo(BASE_URL + "/api/v1/internal/stores?ownerUserId=1"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        server.expect(requestTo(BASE_URL + "/api/v1/internal/stores?ownerUserId=2"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        server.expect(requestTo(BASE_URL + "/api/v1/internal/foundations?representativeId=3"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThat(adapter.resolverEntidadAsociada(1L, Set.of(Rol.STORE_ADMIN)).estaVacia()).isTrue();
        assertThat(adapter.resolverEntidadAsociada(2L, Set.of(Rol.STORE_ADMIN)).estaVacia()).isTrue();
        assertThat(adapter.resolverEntidadAsociada(3L, Set.of(Rol.FOUNDATION_REPRESENTATIVE)).estaVacia()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("Fail-open: sin baseUrl o password retorna vacío sin intentar conexión")
    void sinConfiguracionEsFailOpen() {
        TiendasEntidadAdapter sinUrl = new TiendasEntidadAdapter(RestClient.builder(), "", "ms-auth", "secret");
        TiendasEntidadAdapter sinClave = new TiendasEntidadAdapter(RestClient.builder(), BASE_URL, "ms-auth", "");

        assertThat(sinUrl.resolverEntidadAsociada(1L, Set.of(Rol.STORE_ADMIN)).estaVacia()).isTrue();
        assertThat(sinClave.resolverEntidadAsociada(1L, Set.of(Rol.FOUNDATION_REPRESENTATIVE)).estaVacia()).isTrue();
    }

    @Test
    @DisplayName("Fail-open: error de red/timeout con cliente JDK retorna vacío")
    void timeoutDeRedEsFailOpen() {
        TiendasEntidadAdapter adapter = new TiendasEntidadAdapter(
                RestClient.builder(),
                "http://127.0.0.1:1",
                "ms-auth",
                "secret",
                new MockEnvironment());

        EntidadAsociada resultado = adapter.resolverEntidadAsociada(99L, Set.of(Rol.STORE_ADMIN));
        assertThat(resultado.estaVacia()).isTrue();
    }
}
