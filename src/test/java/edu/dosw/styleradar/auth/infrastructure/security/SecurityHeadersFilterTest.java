package edu.dosw.styleradar.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

@DisplayName("Pruebas Unitarias de SecurityHeadersFilter - OWASP A02:2025")
class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    @DisplayName("Debe inyectar todas las cabeceras de seguridad HTTP y Cache-Control no-store en /api/**")
    void debeInyectarCabecerasDefensivasEnRutasApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_X_CONTENT_TYPE_OPTIONS)).isEqualTo("nosniff");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_X_FRAME_OPTIONS)).isEqualTo("DENY");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_X_XSS_PROTECTION)).isEqualTo("1; mode=block");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_STRICT_TRANSPORT_SECURITY))
                .contains("max-age=31536000");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_CONTENT_SECURITY_POLICY))
                .contains("frame-ancestors 'none'");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_REFERRER_POLICY))
                .isEqualTo("strict-origin-when-cross-origin");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_PERMISSIONS_POLICY))
                .contains("geolocation=()");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_CACHE_CONTROL))
                .isEqualTo("no-store, no-cache, must-revalidate");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_PRAGMA)).isEqualTo("no-cache");

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("Debe inyectar cabeceras generales sin forzar no-store fuera de /api/**")
    void noDebeForzarCacheControlEnRutasNoApi() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_X_FRAME_OPTIONS)).isEqualTo("DENY");
        assertThat(response.getHeader(SecurityHeadersFilter.HEADER_CACHE_CONTROL)).isNull();
        verify(chain).doFilter(request, response);
    }
}

