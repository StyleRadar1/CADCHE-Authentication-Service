package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.dto.request.GoogleCodeLoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.GoogleLoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.GoogleAuthUrlResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import edu.dosw.styleradar.auth.application.service.GoogleOAuth2Service;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.infrastructure.config.SecurityConfig;
import edu.dosw.styleradar.auth.infrastructure.exception.GlobalExceptionHandler;
import edu.dosw.styleradar.auth.infrastructure.security.RateLimitingFilter;

@WebMvcTest(controllers = GoogleOAuth2Controller.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
@DisplayName("Pruebas WebMVC de GoogleOAuth2Controller y Seguridad de Rutas Públicas")
class GoogleOAuth2ControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GoogleOAuth2Service googleOAuth2Service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private OtpService otpService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @Test
    @DisplayName("POST /api/v1/auth/oauth2/google debe emitir JWT 200 OK con ID Token válido sin Header Authorization")
    void autenticarConGoogle_conIdTokenValido_debeRetornar200() throws Exception {
        GoogleLoginRequestDTO request = new GoogleLoginRequestDTO("google.id.token.valido");
        LoginResponseDTO expected = new LoginResponseDTO(
                "styleradar.jwt.token", "Bearer", 86400L, 25L, Set.of(Rol.COMPRADOR));

        when(googleOAuth2Service.autenticarConIdToken("google.id.token.valido")).thenReturn(expected);

        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("styleradar.jwt.token")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.usuarioId", is(25)))
                .andExpect(jsonPath("$.roles[0]", is("COMPRADOR")))
                .andExpect(jsonPath("$.requiereOtp", is(false)));
    }

    @Test
    @DisplayName("POST /api/v1/auth/oauth2/google debe validar Bean Validation (400), 401, 403 y 503")
    void autenticarConGoogle_conErrores_debeMapearCodigosHttp() throws Exception {
        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        when(googleOAuth2Service.autenticarConIdToken("token-invalido"))
                .thenThrow(new CredencialesIncorrectasException("El ID Token de Google es inválido"));
        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"token-invalido\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));

        when(googleOAuth2Service.autenticarConIdToken("token-bloqueado"))
                .thenThrow(new CuentaNoActivaException("Cuenta BLOQUEADA"));
        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"token-bloqueado\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)));

        when(googleOAuth2Service.autenticarConIdToken("token-sin-config"))
                .thenThrow(new ServicioExternoNoDisponibleException("GOOGLE_CLIENT_ID faltante"));
        mockMvc.perform(post("/api/v1/auth/oauth2/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"token-sin-config\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/google/url debe retornar URL y state o 400 ante redirectUri prohibida")
    void obtenerUrlAutorizacion_debeRetornarUrlO400() throws Exception {
        GoogleAuthUrlResponseDTO dto = new GoogleAuthUrlResponseDTO(
                "https://accounts.google.com/o/oauth2/v2/auth?state=abc",
                "abc",
                300L,
                "http://localhost:8100/api/v1/auth/oauth2/google/callback");
        when(googleOAuth2Service.generarUrlAutorizacion(null)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/auth/oauth2/google/url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", is("abc")))
                .andExpect(jsonPath("$.expiresInSeconds", is(300)));

        when(googleOAuth2Service.generarUrlAutorizacion("https://evil.ejemplo"))
                .thenThrow(new IllegalArgumentException("redirectUri no autorizada"));
        mockMvc.perform(get("/api/v1/auth/oauth2/google/url")
                        .param("redirectUri", "https://evil.ejemplo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/google/callback debe completar login, rechazar access_denied (401) o falta de code/state (400)")
    void callbackGoogle_debeManejarExitoCancelacionYParametrosFaltantes() throws Exception {
        LoginResponseDTO expected = new LoginResponseDTO(
                "styleradar.jwt.callback", "Bearer", 86400L, 30L, Set.of(Rol.COMPRADOR));
        when(googleOAuth2Service.autenticarConCodigo("code-ok", "state-ok", null)).thenReturn(expected);

        mockMvc.perform(get("/api/v1/auth/oauth2/google/callback")
                        .param("code", "code-ok")
                        .param("state", "state-ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("styleradar.jwt.callback")))
                .andExpect(jsonPath("$.requiereOtp", is(false)));

        mockMvc.perform(get("/api/v1/auth/oauth2/google/callback")
                        .param("error", "access_denied")
                        .param("state", "state-ok"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("denegada")));

        mockMvc.perform(get("/api/v1/auth/oauth2/google/callback")
                        .param("state", "solo-state"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        verifyNoInteractions(jwtService);
    }

    @Test
    @DisplayName("POST /api/v1/auth/oauth2/google/code debe validar campos obligatorios y delegar el intercambio")
    void autenticarConCodigo_debeRetornar200OValidarDto() throws Exception {
        GoogleCodeLoginRequestDTO request = new GoogleCodeLoginRequestDTO(
                "code-xyz", "state-xyz", "http://localhost:3000/auth/google/callback");
        LoginResponseDTO expected = new LoginResponseDTO(
                "styleradar.jwt.code", "Bearer", 86400L, 90L, Set.of(Rol.ADMIN_STYLERADAR));

        when(googleOAuth2Service.autenticarConCodigo(
                "code-xyz", "state-xyz", "http://localhost:3000/auth/google/callback"))
                .thenReturn(expected);

        mockMvc.perform(post("/api/v1/auth/oauth2/google/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("styleradar.jwt.code")))
                .andExpect(jsonPath("$.roles[0]", is("ADMIN_STYLERADAR")));

        mockMvc.perform(post("/api/v1/auth/oauth2/google/code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"\",\"state\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));

        verify(googleOAuth2Service).autenticarConCodigo(
                "code-xyz", "state-xyz", "http://localhost:3000/auth/google/callback");
    }

    @Test
    @DisplayName("RateLimitingFilter debe proteger las 4 rutas de Google OAuth2 contra fuerza bruta (429)")
    void rateLimitingFilter_debeLimitarEndpointsGoogleOAuth2() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(org.mockito.ArgumentMatchers.anyString())).thenReturn(2L);

        RateLimitingFilter filter = new RateLimitingFilter(
                stringRedisTemplate, objectMapper, new MockEnvironment(), true, 1, 60L, false);

        String[][] routes = {
                {"POST", "/api/v1/auth/oauth2/google"},
                {"POST", "/api/v1/auth/oauth2/google/code"},
                {"GET", "/api/v1/auth/oauth2/google/url"},
                {"GET", "/api/v1/auth/oauth2/google/callback"}
        };

        for (String[] route : routes) {
            MockHttpServletRequest req = new MockHttpServletRequest(route[0], route[1]);
            req.setRemoteAddr("198.51.100.10");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, new MockFilterChain());
            assertEquals(429, res.getStatus(), "Se esperaba 429 en " + route[0] + " " + route[1]);
        }
    }
}
