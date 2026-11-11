package edu.dosw.styleradar.auth.infrastructure.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;

import edu.dosw.styleradar.auth.application.dto.response.ErrorResponseDTO;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;

@DisplayName("Pruebas Unitarias de GlobalExceptionHandler - OWASP A09 / A10:2025")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Debe sanitizar rutas con CRLF y mapear cada excepción de seguridad y fallo de dependencias")
    void debeMapearExcepcionesYSanitizarPath() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login\r\nforged");

        ResponseEntity<ErrorResponseDTO> r400 = handler.handleInvalidHttpRequest(
                new IllegalArgumentException("Entrada inválida"), request);
        assertThat(r400.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(r400.getBody().getPath()).doesNotContain("\r").doesNotContain("\n");

        ResponseEntity<ErrorResponseDTO> r401 = handler.handleUnauthorized(
                new CredencialesIncorrectasException("Credenciales inválidas"), request);
        assertThat(r401.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ErrorResponseDTO> r401Spring = handler.handleUnauthorized(
                new BadCredentialsException("Bad token"), request);
        assertThat(r401Spring.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ErrorResponseDTO> r403Cuenta = handler.handleForbidden(
                new CuentaNoActivaException("Cuenta bloqueada"), request);
        assertThat(r403Cuenta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ErrorResponseDTO> r403Access = handler.handleForbidden(
                new AccessDeniedException("Sin permisos"), request);
        assertThat(r403Access.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ErrorResponseDTO> r405 = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"), request);
        assertThat(r405.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<ErrorResponseDTO> rOtpInv = handler.handleOtpInvalido(
                new OtpInvalidoException("OTP incorrecto"), request);
        assertThat(rOtpInv.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ErrorResponseDTO> rOtpExp = handler.handleOtpInvalido(
                new OtpExpiradoException("OTP expirado"), request);
        assertThat(rOtpExp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ErrorResponseDTO> r429 = handler.handleOtpIntentosAgotados(
                new OtpIntentosAgotadosException("Intentos agotados"), request);
        assertThat(r429.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);

        ResponseEntity<ErrorResponseDTO> r503 = handler.handleExternalServiceUnavailable(
                new ServicioExternoNoDisponibleException("Redis caído"), request);
        assertThat(r503.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r503.getBody().getMessage()).doesNotContain("Redis caído");

        ResponseEntity<ErrorResponseDTO> r500 = handler.handleUnexpected(
                new RuntimeException("Detalle interno SQL/JWT sensible"), request);
        assertThat(r500.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r500.getBody().getMessage())
                .doesNotContain("SQL")
                .doesNotContain("JWT")
                .contains("error interno");
    }
}

