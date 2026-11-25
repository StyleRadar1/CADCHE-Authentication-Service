package edu.dosw.styleradar.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

@DisplayName("Pruebas Unitarias de CorrelationIdFilter - BUG-031")
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    @DisplayName("Propaga X-Correlation-Id a MDC y cabeceras de respuesta, limpiando MDC al final")
    void propagaXCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader(CorrelationIdFilter.HEADER_CORRELATION_ID, "trace-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcEnCadena = new AtomicReference<>();

        FilterChain chain = (req, res) -> mdcEnCadena.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        filter.doFilter(request, response, chain);

        assertThat(mdcEnCadena.get()).isEqualTo("trace-12345");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_CORRELATION_ID)).isEqualTo("trace-12345");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER_REQUEST_ID)).isEqualTo("trace-12345");
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Acepta X-Request-Id como respaldo, sanitiza CRLF y genera UUID si faltan ambos")
    void usaXRequestIdOGeneraUuid() throws Exception {
        MockHttpServletRequest reqConRequestId = new MockHttpServletRequest("GET", "/actuator/health");
        reqConRequestId.addHeader(CorrelationIdFilter.HEADER_REQUEST_ID, "req-99\r\n");
        MockHttpServletResponse resConRequestId = new MockHttpServletResponse();

        filter.doFilter(reqConRequestId, resConRequestId, new MockFilterChain());
        assertThat(resConRequestId.getHeader(CorrelationIdFilter.HEADER_CORRELATION_ID)).isEqualTo("req-99");

        MockHttpServletRequest reqSinHeaders = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse resSinHeaders = new MockHttpServletResponse();

        filter.doFilter(reqSinHeaders, resSinHeaders, new MockFilterChain());
        assertThat(resSinHeaders.getHeader(CorrelationIdFilter.HEADER_CORRELATION_ID)).isNotBlank();
        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
