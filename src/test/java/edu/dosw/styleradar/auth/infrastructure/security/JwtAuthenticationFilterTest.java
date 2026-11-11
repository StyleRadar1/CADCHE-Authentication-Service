package edu.dosw.styleradar.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;

@DisplayName("Pruebas Unitarias de JwtAuthenticationFilter - OWASP A01 / A07 / A10:2025")
class JwtAuthenticationFilterTest {

    private JwtService jwtService;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private OtpService otpService;
    private ObjectMapper objectMapper;
    private FilterChain filterChain;
    private MockEnvironment environment;
    private JwtAuthenticationFilter filter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        jwtService = mock(JwtService.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        otpService = mock(OtpService.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        filterChain = mock(FilterChain.class);
        environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        filter = new JwtAuthenticationFilter(jwtService, redisTemplate, otpService, objectMapper, environment);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Debe continuar sin autenticar cuando no hay cabecera Bearer")
    void debeContinuarSinCabeceraBearer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/logout");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Debe rechazar token Bearer vacío con 401 JSON")
    void debeRechazarTokenVacioCon401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Token inv");
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Debe autenticar el contexto con roles válidos e ignorar claims desconocidos")
    void debeAutenticarConRolesValidos() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer jwt.valido");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = Jwts.claims()
                .subject("42")
                .issuedAt(new Date(5000L))
                .add("roles", List.of("COMPRADOR", "ROL_DESCONOCIDO"))
                .build();

        when(jwtService.validarToken("jwt.valido")).thenReturn(claims);
        when(otpService.esTokenInvalido("jwt.valido")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("usuario:revocado:42")).thenReturn("1000");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("42");
        assertThat(auth.getAuthorities()).extracting("authority").containsExactly("ROLE_COMPRADOR");
    }

    @Test
    @DisplayName("Debe rechazar con 401 si el token está en lista negra o le faltan claims obligatorios")
    void debeRechazarTokenEnBlacklistOIncompleto() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer jwt.blacklisted");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = Jwts.claims().subject("42").issuedAt(new Date()).build();
        when(jwtService.validarToken("jwt.blacklisted")).thenReturn(claims);
        when(otpService.esTokenInvalido("jwt.blacklisted")).thenReturn(true);

        filter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);

        MockHttpServletResponse incompleteResp = new MockHttpServletResponse();
        when(jwtService.validarToken("jwt.blacklisted")).thenReturn(Jwts.claims().subject("42").build());
        filter.doFilter(request, incompleteResp, filterChain);
        assertThat(incompleteResp.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Debe rechazar con 401 cuando el usuario fue revocado globalmente tras emitir el token")
    void debeRechazarPorRevocacionGlobalYCorrupcion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer jwt.antiguo");
        MockHttpServletResponse response = new MockHttpServletResponse();

        Claims claims = Jwts.claims().subject("42").issuedAt(new Date(1000L)).build();
        when(jwtService.validarToken("jwt.antiguo")).thenReturn(claims);
        when(otpService.esTokenInvalido("jwt.antiguo")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("usuario:revocado:42")).thenReturn("2000");

        filter.doFilter(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(401);

        MockHttpServletResponse corruptResp = new MockHttpServletResponse();
        when(valueOperations.get("usuario:revocado:42")).thenReturn("fecha-invalida");
        filter.doFilter(request, corruptResp, filterChain);
        assertThat(corruptResp.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Debe rechazar con 401 ante JwtException")
    void debeRechazarAnteJwtException() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer jwt.roto");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(jwtService.validarToken("jwt.roto")).thenThrow(new JwtException("firma inválida"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Debe fallar cerrado con 503 fuera de dev cuando Redis falla y permitir degradación solo en dev")
    void debeFallarCerradoEnProdCuandoRedisFalla() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
        request.addHeader("Authorization", "Bearer jwt.valido");
        MockHttpServletResponse prodResponse = new MockHttpServletResponse();

        Claims claims = Jwts.claims().subject("42").issuedAt(new Date()).build();
        when(jwtService.validarToken("jwt.valido")).thenReturn(claims);
        when(otpService.esTokenInvalido("jwt.valido")).thenReturn(false);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("usuario:revocado:42"))
                .thenThrow(new DataAccessResourceFailureException("Redis down"));

        filter.doFilter(request, prodResponse, filterChain);
        assertThat(prodResponse.getStatus()).isEqualTo(503);
        verifyNoInteractions(filterChain);

        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        JwtAuthenticationFilter devFilter =
                new JwtAuthenticationFilter(jwtService, redisTemplate, otpService, objectMapper, devEnv);
        MockHttpServletResponse devResponse = new MockHttpServletResponse();

        devFilter.doFilter(request, devResponse, filterChain);
        verify(filterChain).doFilter(request, devResponse);
    }
}
