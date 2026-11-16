package edu.dosw.styleradar.auth.infrastructure.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import edu.dosw.styleradar.auth.application.dto.response.ErrorResponseDTO;
import edu.dosw.styleradar.auth.domain.exception.AuthDomainException;
import edu.dosw.styleradar.auth.domain.model.CodigoError;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * Traduce las excepciones a la estructura única de error del contrato v1.2 (sección 1.4): estado HTTP,
 * {@code codigo} del catálogo, {@code intentosRestantes}, {@code esperarSegundos} y cabecera
 * {@code Retry-After} en todos los 429. El dominio nunca conoce HTTP.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private static final String DATOS_INVALIDOS = "La solicitud contiene datos inválidos";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {
        return datosInvalidos(exception, request);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponseDTO> handleBindingValidation(
            BindException exception, HttpServletRequest request) {
        return datosInvalidos(exception, request);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ErrorResponseDTO> handleInvalidHttpRequest(
            Exception exception, HttpServletRequest request) {
        return build(CodigoError.DATOS_INVALIDOS, "La solicitud contiene parámetros inválidos",
                request, Map.of(), null, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return build(CodigoError.METODO_NO_PERMITIDO, "El método HTTP no está permitido para esta ruta",
                request, Map.of(), null, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponseDTO> handleAuthentication(
            AuthenticationException exception, HttpServletRequest request) {
        return build(CodigoError.TOKEN_INVALIDO, "El token presentado es inválido o expirado",
                request, Map.of(), null, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(
            AccessDeniedException exception, HttpServletRequest request) {
        log.warn("Acceso denegado (403) en ruta {}", SecuritySanitizer.sanitizeForLog(request.getRequestURI()));
        return build(CodigoError.ACCESO_DENEGADO, "Acceso denegado: no cuenta con permisos suficientes",
                request, Map.of(), null, null);
    }

    @ExceptionHandler(AuthDomainException.class)
    public ResponseEntity<ErrorResponseDTO> handleDomain(AuthDomainException exception, HttpServletRequest request) {
        CodigoError codigo = exception.getCodigo();
        String ruta = SecuritySanitizer.sanitizeForLog(request.getRequestURI());
        String mensaje = exception.getMessage();

        if (codigo == CodigoError.SERVICIO_NO_DISPONIBLE) {
            log.error("Dependencia externa no disponible en ruta {}: {}", ruta, SecuritySanitizer.sanitizeForLog(mensaje));
            mensaje = "El servicio requerido no está disponible temporalmente. Intente nuevamente más tarde.";
        } else if (codigo.estadoHttp() == HttpStatus.FORBIDDEN.value()
                || codigo.estadoHttp() == HttpStatus.TOO_MANY_REQUESTS.value()) {
            log.warn("Solicitud rechazada ({}) en ruta {}: {}", codigo, ruta, SecuritySanitizer.sanitizeForLog(mensaje));
        }

        return build(codigo, mensaje, request, Map.of(), exception.getIntentosRestantes(), exception.getEsperarSegundos());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Error no controlado al procesar la ruta {}",
                SecuritySanitizer.sanitizeForLog(request.getRequestURI()), exception);
        return build(CodigoError.ERROR_INTERNO, "Ocurrió un error interno. Intente nuevamente más tarde.",
                request, Map.of(), null, null);
    }

    private ResponseEntity<ErrorResponseDTO> datosInvalidos(BindException exception, HttpServletRequest request) {
        Map<String, String> validationErrors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return build(CodigoError.DATOS_INVALIDOS, DATOS_INVALIDOS, request, validationErrors, null, null);
    }

    private ResponseEntity<ErrorResponseDTO> build(
            CodigoError codigo,
            String message,
            HttpServletRequest request,
            Map<String, String> validationErrors,
            Integer intentosRestantes,
            Long esperarSegundos) {

        HttpStatus status = HttpStatus.valueOf(codigo.estadoHttp());
        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .codigo(codigo)
                .message(message)
                .path(SecuritySanitizer.sanitizeForLog(request.getRequestURI()))
                .validationErrors(validationErrors)
                .intentosRestantes(intentosRestantes)
                .esperarSegundos(esperarSegundos)
                .build();

        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(status);
        if (status == HttpStatus.TOO_MANY_REQUESTS && esperarSegundos != null) {
            respuesta.header(HttpHeaders.RETRY_AFTER, String.valueOf(esperarSegundos));
        }
        return respuesta.body(errorResponse);
    }
}
