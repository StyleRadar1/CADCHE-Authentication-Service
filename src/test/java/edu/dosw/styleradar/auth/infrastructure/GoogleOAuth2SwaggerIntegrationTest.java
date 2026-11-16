package edu.dosw.styleradar.auth.infrastructure;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Pruebas de Contrato OpenAPI / Swagger UI y Seguridad Integrada Google OAuth2")
class GoogleOAuth2SwaggerIntegrationTest {

    @DynamicPropertySource
    static void registerRuntimeSecurityProps(DynamicPropertyRegistry registry) {
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        String ephemeralJwtKey = Base64.getEncoder().encodeToString(randomBytes);
        registry.add("styleradar.security.jwt.secret-base64", () -> ephemeralJwtKey);
        registry.add("styleradar.security.rate-limit.enabled", () -> "false");
        registry.add("styleradar.security.oauth2.google.client-id", () -> "");
        registry.add("styleradar.security.oauth2.google.client-secret", () -> "");
        registry.add("spring.rabbitmq.listener.simple.auto-startup", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JavaMailSender javaMailSender;

    @Test
    @DisplayName("OpenAPI /v3/api-docs debe publicar las rutas de Google OAuth2 sin bloqueo bearerAuth")
    void apiDocs_debeExponerEndpointsGoogleOAuth2YSinSeguridadObligatoria() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/auth/oauth2/google'].post.security.length()", is(0)))
                .andExpect(jsonPath("$.paths['/api/v1/auth/oauth2/google/url'].get.security.length()", is(0)))
                .andExpect(jsonPath("$.paths['/api/v1/auth/oauth2/google/callback'].get.security.length()", is(0)))
                .andExpect(jsonPath("$.paths['/api/v1/auth/oauth2/google/code'].post.security.length()", is(0)))
                .andExpect(jsonPath("$.paths['/api/v1/auth/oauth2/google'].post.summary",
                        containsString("Google")))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login'].post.security.length()", is(0)))
                .andExpect(jsonPath("$.paths['/api/v1/auth/logout'].post.security.length()", not(is(0))));
    }

    @Test
    @DisplayName("Rutas Google OAuth2 deben permitir acceso sin Bearer y responder 503 fail-closed cuando falta GOOGLE_CLIENT_ID")
    void endpointsGoogle_cuandoFaltaGoogleClientId_debenResponder503FailClosed() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/google/url"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)))
                .andExpect(jsonPath("$.message", containsString("disponible temporalmente")));

        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"token.sintetico\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)));
    }
}
