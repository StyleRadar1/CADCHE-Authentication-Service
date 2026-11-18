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
    @DisplayName("401 por token revocado o mal formado responde siempre el texto de INVALID_TOKEN")
    void tokenInvalidoUsaMensajeDelContrato() throws Exception {
        JsonNode cuerpo = escribir(HttpStatus.UNAUTHORIZED, "El token ha sido revocado");

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
}
