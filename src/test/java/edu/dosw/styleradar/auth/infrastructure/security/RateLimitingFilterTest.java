package edu.dosw.styleradar.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;

@DisplayName("Pruebas Unitarias de RateLimitingFilter - OWASP A06 / A07 / A10:2025")
class RateLimitingFilterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private ObjectMapper objectMapper;
    private FilterChain filterChain;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        objectMapper = new ObjectMapper().findAndRegisterModules();
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Debe permitir peticiones POST debajo del límite y bloquear exceso con 429 + Retry-After")
    void debeBloquearCuandoExcedeMaximoDeSolicitudes() throws Exception {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        RateLimitingFilter filter = new RateLimitingFilter(
                redisTemplate, objectMapper, prodEnv, true, 2, 60L, false);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:/api/v1/auth/login:10.0.0.5"))
                .thenReturn(1L, 2L, 3L);

        for (int i = 0; i < 2; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            req.setRemoteAddr("10.0.0.5");
            MockHttpServletResponse res = new MockHttpServletResponse();
            filter.doFilter(req, res, filterChain);
            assertThat(res.getStatus()).isEqualTo(200);
        }
        verify(filterChain, times(2)).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());

        MockHttpServletRequest blockedReq = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        blockedReq.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse blockedRes = new MockHttpServletResponse();

        filter.doFilter(blockedReq, blockedRes, filterChain);

        assertThat(blockedRes.getStatus()).isEqualTo(429);
        assertThat(blockedRes.getHeader("Retry-After")).isEqualTo("60");
        assertThat(blockedRes.getContentAsString()).contains("Demasiadas solicitudes");
    }

    @Test
    @DisplayName("Debe omitir limitación en métodos GET o rutas no públicas de autenticación")
    void debeOmitirMetodosGetYRutasNoProtegidas() throws Exception {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        RateLimitingFilter filter = new RateLimitingFilter(
                redisTemplate, objectMapper, prodEnv, true, 1, 60L, false);

        MockHttpServletRequest getReq = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse getRes = new MockHttpServletResponse();

        filter.doFilter(getReq, getRes, filterChain);

        verify(filterChain).doFilter(getReq, getRes);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Debe fallar cerrado con 503 fuera de dev cuando Redis no está disponible")
    void debeFallarCerradoCuandoRedisFallaFueraDeDev() throws Exception {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        RateLimitingFilter filter = new RateLimitingFilter(
                redisTemplate, objectMapper, prodEnv, true, 5, 60L, false);

        when(redisTemplate.opsForValue()).thenThrow(new DataAccessResourceFailureException("Redis down"));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/otp/verificar");
        req.setRemoteAddr("10.0.0.8");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, filterChain);

        assertThat(res.getStatus()).isEqualTo(503);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Debe usar contador en memoria local exclusivamente en perfil dev cuando Redis falla")
    void debeUsarRespaldoLocalEnDevYLimitarPeticiones() throws Exception {
        MockEnvironment devEnv = new MockEnvironment();
        devEnv.setActiveProfiles("dev");
        RateLimitingFilter filter = new RateLimitingFilter(
                redisTemplate, objectMapper, devEnv, true, 1, 60L, true);

        when(redisTemplate.opsForValue()).thenThrow(new DataAccessResourceFailureException("Redis down"));

        MockHttpServletRequest req1 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req1.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res1 = new MockHttpServletResponse();
        filter.doFilter(req1, res1, filterChain);
        verify(filterChain).doFilter(req1, res1);

        MockHttpServletRequest req2 = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req2.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse res2 = new MockHttpServletResponse();
        filter.doFilter(req2, res2, filterChain);
        assertThat(res2.getStatus()).isEqualTo(429);
    }
}
