package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc;

import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_CUENTA_NO_ACTIVA;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_DATOS_INVALIDOS;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_LIMITE;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_NO_DISPONIBLE;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_OTP_AGOTADO;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_OTP_INCORRECTO;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_OTP_VENCIDO;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import edu.dosw.styleradar.auth.application.dto.request.RestablecerContrasenaRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.SolicitarRestablecimientoRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.ErrorResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.RestablecimientoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * Forgotten-password reset with a one-time code (OWASP Forgot Password Cheat Sheet). Works for every account
 * type: buyer, store administrator, foundation representative and StyleRadar administrator.
 */
@Tag(name = "Password reset", description = "Reset a forgotten password with a 6-digit code sent by email")
public interface RestablecerContrasenaApi {

    String JSON = "application/json";
    String PROBLEMA = ErrorResponseDTO.MEDIA_TYPE;
    String SOLICITUD = "{\"message\":\"Si el correo está registrado, te enviamos un código para restablecer tu contraseña.\",\"expiresInSeconds\":300}";
    String CAMBIADA = "{\"message\":\"Tu contraseña se actualizó. Inicia sesión con la nueva contraseña.\"}";

    @Operation(
            summary = "Request a password reset code",
            description = "Always answers 202 with the same body, whether or not the email belongs to an account, "
                    + "so registered emails cannot be discovered. If it belongs to an active account, sends a 6-digit code "
                    + "valid for 5 minutes. Calling it again within 15 minutes resends a new code (at most 3 resends).",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = SolicitarRestablecimientoRequestDTO.class),
                    examples = @ExampleObject(name = "Forgot", value = "{\"email\":\"tienda@almacenaliado.co\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Request accepted (same answer for any email)", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = RestablecimientoResponseDTO.class),
                    examples = @ExampleObject(name = "Accepted", value = SOLICITUD))),
            @ApiResponse(responseCode = "400", description = "INVALID_DATA", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "INVALID_DATA", value = ERROR_DATOS_INVALIDOS))),
            @ApiResponse(responseCode = "429", description = "RATE_LIMIT_EXCEEDED",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait", schema = @Schema(type = "integer")),
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "RATE_LIMIT_EXCEEDED", value = ERROR_LIMITE))),
            @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: ms-usuarios, SMTP or Redis not responding",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "SERVICE_UNAVAILABLE", value = ERROR_NO_DISPONIBLE)))
    })
    ResponseEntity<RestablecimientoResponseDTO> solicitar(@Valid @RequestBody SolicitarRestablecimientoRequestDTO request);

    @Operation(
            summary = "Reset the password with the code",
            description = "Validates the code (3 attempts, then a 60 s wait) and replaces the password in ms-usuarios. "
                    + "Does not start a session: every open session of the account is revoked and the user must log in "
                    + "again with the new password. An email notice confirms the change.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = RestablecerContrasenaRequestDTO.class),
                    examples = @ExampleObject(name = "Reset",
                            value = "{\"email\":\"tienda@almacenaliado.co\",\"otpCode\":\"482915\",\"newPassword\":\"NuevaClave2026*\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed; sessions revoked", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = RestablecimientoResponseDTO.class),
                    examples = @ExampleObject(name = "Changed", value = CAMBIADA))),
            @ApiResponse(responseCode = "400", description = "INVALID_DATA: code not 6 digits or weak password", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "INVALID_DATA", value = ERROR_DATOS_INVALIDOS))),
            @ApiResponse(responseCode = "401", description = "OTP_INCORRECT (with attemptsRemaining) or OTP_EXPIRED",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class), examples = {
                            @ExampleObject(name = "OTP_INCORRECT", value = ERROR_OTP_INCORRECTO),
                            @ExampleObject(name = "OTP_EXPIRED", value = ERROR_OTP_VENCIDO)
                    })),
            @ApiResponse(responseCode = "403", description = "ACCOUNT_NOT_ACTIVE", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "ACCOUNT_NOT_ACTIVE", value = ERROR_CUENTA_NO_ACTIVA))),
            @ApiResponse(responseCode = "429", description = "OTP_ATTEMPTS_EXHAUSTED (retryAfterSeconds=60) or RATE_LIMIT_EXCEEDED",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait", schema = @Schema(type = "integer")),
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "OTP_ATTEMPTS_EXHAUSTED", value = ERROR_OTP_AGOTADO))),
            @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: ms-usuarios or Redis not responding",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "SERVICE_UNAVAILABLE", value = ERROR_NO_DISPONIBLE)))
    })
    ResponseEntity<RestablecimientoResponseDTO> restablecer(@Valid @RequestBody RestablecerContrasenaRequestDTO request);
}
