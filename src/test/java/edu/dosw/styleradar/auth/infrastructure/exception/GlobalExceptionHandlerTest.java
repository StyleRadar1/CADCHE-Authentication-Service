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
import edu.dosw.styleradar.auth.domain.model.CodigoError;
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

        ResponseEntity<ErrorResponseDTO> r401 = handler.handleDomain(
                new CredencialesIncorrectasException("Credenciales inválidas"), request);
        assertThat(r401.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ErrorResponseDTO> r401Spring = handler.handleAuthentication(
                new BadCredentialsException("Bad token"), request);
        assertThat(r401Spring.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ErrorResponseDTO> r403Cuenta = handler.handleDomain(
                new CuentaNoActivaException("Cuenta bloqueada"), request);
        assertThat(r403Cuenta.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ErrorResponseDTO> r403Access = handler.handleAccessDenied(
                new AccessDeniedException("Sin permisos"), request);
        assertThat(r403Access.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<ErrorResponseDTO> r405 = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("DELETE"), request);
        assertThat(r405.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        ResponseEntity<ErrorResponseDTO> rOtpInv = handler.handleDomain(
                new OtpInvalidoException(1), request);
        assertThat(rOtpInv.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ErrorResponseDTO> rOtpExp = handler.handleDomain(
                new OtpExpiradoException("OTP expirado"), request);
        assertThat(rOtpExp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<ErrorResponseDTO> r429 = handler.handleDomain(
                OtpIntentosAgotadosException.intentosAgotados(60L), request);
        assertThat(r429.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(r429.getHeaders().getFirst("Retry-After")).isEqualTo("60");
        assertThat(r429.getBody().getCode()).isEqualTo(CodigoError.OTP_ATTEMPTS_EXHAUSTED);
        assertThat(r429.getBody().getRetryAfterSeconds()).isEqualTo(60L);
        assertThat(rOtpInv.getBody().getCode()).isEqualTo(CodigoError.OTP_INCORRECT);
        assertThat(rOtpInv.getBody().getAttemptsRemaining()).isEqualTo(1);
        assertThat(r401Spring.getBody().getCode()).isEqualTo(CodigoError.INVALID_TOKEN);
        assertThat(r403Access.getBody().getCode()).isEqualTo(CodigoError.ACCESS_DENIED);
        assertThat(r405.getBody().getCode()).isEqualTo(CodigoError.METHOD_NOT_ALLOWED);

        ResponseEntity<ErrorResponseDTO> r503 = handler.handleDomain(
                new ServicioExternoNoDisponibleException("Redis caído"), request);
        assertThat(r503.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(r503.getBody().getMessage()).doesNotContain("Redis caído");
        assertThat(r503.getBody().getCode()).isEqualTo(CodigoError.SERVICE_UNAVAILABLE);

        ResponseEntity<ErrorResponseDTO> r500 = handler.handleUnexpected(
                new RuntimeException("Detalle interno SQL/JWT sensible"), request);
        assertThat(r500.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(r500.getBody().getCode()).isEqualTo(CodigoError.INTERNAL_ERROR);
        assertThat(r500.getBody().getMessage())
                .doesNotContain("SQL")
                .doesNotContain("JWT")
                .contains("error interno");
    }

    @Test
    @DisplayName("Incluye mensajes específicos de validación en detail y message, y sanitiza errores HTTP y typeMismatch")
    void debeIncluirDetalleClaroEnErroresDeValidacionYHttp() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/password/reset");

        org.springframework.validation.BeanPropertyBindingResult binding =
                new org.springframework.validation.BeanPropertyBindingResult(new Object(), "reset");
        binding.addError(new org.springframework.validation.FieldError(
                "reset", "newPassword", null, false, new String[] {"Pattern"}, null,
                "La contraseña debe incluir al menos una mayúscula, una minúscula, un número y un carácter especial."));
        binding.addError(new org.springframework.validation.FieldError(
                "reset", "otpCode", "abc", true, new String[] {"typeMismatch"}, null,
                "Failed to convert property value of type 'java.lang.String'"));

        ResponseEntity<ErrorResponseDTO> rVal = handler.handleBindingValidation(
                new org.springframework.validation.BindException(binding), request);
        assertThat(rVal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(rVal.getBody().getCode()).isEqualTo(CodigoError.INVALID_DATA);
        assertThat(rVal.getBody().getDetail())
                .startsWith("La solicitud contiene datos inválidos: ")
                .contains("un carácter especial")
                .contains("El valor ingresado para 'otpCode' no tiene un formato válido")
                .doesNotContain("java.lang.String");
        assertThat(rVal.getBody().getMessage()).isEqualTo(rVal.getBody().getDetail());
        assertThat(rVal.getBody().getErrors()).containsKey("newPassword").containsKey("otpCode");

        assertThat(GlobalExceptionHandler.resumirErroresValidacion(java.util.Map.of()))
                .isEqualTo("La solicitud contiene datos inválidos");

        ResponseEntity<ErrorResponseDTO> rJson = handler.handleInvalidHttpRequest(
                new org.springframework.http.converter.HttpMessageNotReadableException("bad json", (org.springframework.http.HttpInputMessage) null),
                request);
        assertThat(rJson.getBody().getDetail()).contains("JSON está mal formado");

        ResponseEntity<ErrorResponseDTO> rMedia = handler.handleInvalidHttpRequest(
                new org.springframework.web.HttpMediaTypeNotSupportedException("text/plain"), request);
        assertThat(rMedia.getBody().getDetail()).contains("application/json");

        ResponseEntity<ErrorResponseDTO> rMismatch = handler.handleInvalidHttpRequest(
                new org.springframework.web.method.annotation.MethodArgumentTypeMismatchException("x", Long.class, "id", null, null),
                request);
        assertThat(rMismatch.getBody().getDetail()).contains("tipo o formato inválido");
    }
}

