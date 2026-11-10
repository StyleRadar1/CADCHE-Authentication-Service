package edu.dosw.styleradar.auth.infrastructure.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import edu.dosw.styleradar.auth.application.dto.response.ErrorResponseDTO;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return buildResponse(HttpStatus.BAD_REQUEST,
                "La solicitud contiene datos inválidos",
                request.getRequestURI(), validationErrors);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponseDTO> handleBindingValidation(
            BindException exception, HttpServletRequest request) {

        Map<String, String> validationErrors = new LinkedHashMap<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                validationErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        return buildResponse(HttpStatus.BAD_REQUEST,
                "La solicitud contiene datos inválidos",
                request.getRequestURI(), validationErrors);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ErrorResponseDTO> handleInvalidHttpRequest(
            Exception exception, HttpServletRequest request) {

        return buildResponse(HttpStatus.BAD_REQUEST,
                "La solicitud contiene parámetros inválidos",
                request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {

        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED,
                "El método HTTP no está permitido para esta ruta",
                request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(CredencialesIncorrectasException.class)
    public ResponseEntity<ErrorResponseDTO> handleCredencialesIncorrectas(
            CredencialesIncorrectasException exception, HttpServletRequest request) {

        return buildResponse(HttpStatus.UNAUTHORIZED,
                exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(CuentaNoActivaException.class)
    public ResponseEntity<ErrorResponseDTO> handleCuentaNoActiva(
            CuentaNoActivaException exception, HttpServletRequest request) {

        return buildResponse(HttpStatus.FORBIDDEN,
                exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler({OtpInvalidoException.class, OtpExpiradoException.class})
    public ResponseEntity<ErrorResponseDTO> handleOtpInvalido(
            RuntimeException exception, HttpServletRequest request) {

        return buildResponse(HttpStatus.UNAUTHORIZED,
                exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(OtpIntentosAgotadosException.class)
    public ResponseEntity<ErrorResponseDTO> handleOtpIntentosAgotados(
            OtpIntentosAgotadosException exception, HttpServletRequest request) {

        return buildResponse(HttpStatus.TOO_MANY_REQUESTS,
                exception.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleUnexpected(
            Exception exception, HttpServletRequest request) {

        log.error("Error no controlado al procesar la ruta {}",
                request.getRequestURI(), exception);

        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error interno. Intente nuevamente más tarde.",
                request.getRequestURI(), Map.of());
    }

    private ResponseEntity<ErrorResponseDTO> buildResponse(
            HttpStatus status, String message, String path,
            Map<String, String> validationErrors) {

        ErrorResponseDTO errorResponse = ErrorResponseDTO.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .validationErrors(validationErrors)
                .build();

        return ResponseEntity.status(status).body(errorResponse);
    }
}

