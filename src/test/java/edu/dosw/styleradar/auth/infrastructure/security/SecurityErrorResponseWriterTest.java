package edu.dosw.styleradar.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@DisplayName("SecurityErrorResponseWriter: mensajes exactos del catálogo 1.5 del contrato")
class SecurityErrorResponseWriterTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    private JsonNode escribir(HttpStatus estado, String detalleInterno) throws Exception {
        MockHttpServletResponse respuesta = new MockHttpServletResponse();
        SecurityErrorResponseWriter.write(respuesta, mapper, estado, "/api/v1/auth/logout", detalleInterno);
        assertThat(respuesta.getContentType()).startsWith("application/problem+json");
        return mapper.readTree(respuesta.getContentAsString());
    }

    @Test
    @DisplayName("401: sesión revocada con su mensaje (SR-RF-AUTH-01); token mal formado con el texto de INVALID_TOKEN")
    void tokenInvalidoUsaMensajeDelContrato() throws Exception {
        JsonNode revocado = escribir(HttpStatus.UNAUTHORIZED, "El token ha sido revocado");
        // SR-RF-AUTH-01 FA-05: la sesión revocada por cambio de estado conserva su mensaje propio.
        assertThat(revocado.get("code").asText()).isEqualTo("INVALID_TOKEN");
        assertThat(revocado.get("detail").asText()).isEqualTo("El token ha sido revocado");
        JsonNode cuerpo = escribir(HttpStatus.UNAUTHORIZED, "Firma JWT inválida");

        assertThat(cuerpo.get("code").asText()).isEqualTo("INVALID_TOKEN");
        assertThat(cuerpo.get("message").asText()).isEqualTo("El token presentado es inválido o expirado");
        assertThat(cuerpo.get("detail").asText()).isEqualTo("El token presentado es inválido o expirado");
    }

    @Test
    @DisplayName("503 de Redis responde el texto de SERVICE_UNAVAILABLE sin detalles internos")
    void servicioNoDisponibleUsaMensajeDelContrato() throws Exception {
        JsonNode cuerpo = escribir(HttpStatus.SERVICE_UNAVAILABLE, "El servicio de verificación de sesiones no está disponible");

        assertThat(cuerpo.get("code").asText()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(cuerpo.get("message").asText())
                .isEqualTo("El servicio requerido no está disponible temporalmente. Intente nuevamente más tarde.");
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("SR-RF-112 (v6): los tres mensajes 401 del análisis se responden tal cual; otros se reemplazan por el catálogo")
    void mensajesPropiosDeCerrarSesion() throws Exception {
        for (String propio : java.util.List.of(SecurityErrorResponseWriter.MENSAJE_SIN_BEARER,
                SecurityErrorResponseWriter.MENSAJE_TOKEN_MALFORMADO, SecurityErrorResponseWriter.MENSAJE_TOKEN_INVALIDO,
                SecurityErrorResponseWriter.MENSAJE_SESION_REVOCADA)) {
            org.springframework.mock.web.MockHttpServletResponse r = new org.springframework.mock.web.MockHttpServletResponse();
            SecurityErrorResponseWriter.write(r, null, org.springframework.http.HttpStatus.UNAUTHORIZED, "/api/v1/auth/logout", propio);
            org.assertj.core.api.Assertions.assertThat(r.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains(propio);
        }
        org.springframework.mock.web.MockHttpServletResponse otro = new org.springframework.mock.web.MockHttpServletResponse();
        SecurityErrorResponseWriter.write(otro, null, org.springframework.http.HttpStatus.UNAUTHORIZED, "/x", "detalle técnico");
        org.assertj.core.api.Assertions.assertThat(otro.getContentAsString(java.nio.charset.StandardCharsets.UTF_8))
                .contains(SecurityErrorResponseWriter.MENSAJE_TOKEN_INVALIDO).doesNotContain("detalle técnico");
    }
}
