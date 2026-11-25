package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc;

import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_CREDENCIALES;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_CUENTA_NO_ACTIVA;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_DATOS_INVALIDOS;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_LIMITE;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_NO_DISPONIBLE;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_OTP_AGOTADO;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_OTP_INCORRECTO;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_OTP_VENCIDO;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_REENVIOS_AGOTADOS;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_REENVIO_BLOQUEADO;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_SESION_INEXISTENTE;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.ERROR_TOKEN_INVALIDO;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.LOGIN_ADMIN;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.LOGIN_COMPRADOR;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.LOGIN_FUNDACION;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.LOGIN_TIENDA;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.OTP_REENVIADO;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.SESION_CON_TOKEN;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.SESION_PENDIENTE_OTP;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import edu.dosw.styleradar.auth.application.dto.request.LoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.ReenviarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RefreshTokenRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.VerificarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.ErrorResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.OtpMensajeResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * OpenAPI contract of ms-auth (requirements analysis v2). Every error follows {@link ErrorResponseDTO}: the front end
 * picks the text by {@code code} and every 429 carries {@code retryAfterSeconds} and the {@code Retry-After} header.
 */
@Tag(name = "Authentication", description = "Login, one-time email verification with an OTP code, token refresh and logout with revocation in Redis 7")
public interface AuthApi {

    String JSON = "application/json";
    String PROBLEMA = ErrorResponseDTO.MEDIA_TYPE;

    @Operation(
            summary = "Log in with email and password",
            description = "Validates the credentials against ms-usuarios (8120) and requires an ACTIVE account (RN-03). "
                    + "If the email is already verified it returns the JWT right away (RN-05, otpRequired=false). "
                    + "Otherwise it emails a 6-digit OTP code (300 s, 3 attempts) and answers otpRequired=true without token (FA-13). "
                    + "During the 60 s wait after 3 failures it answers 429 OTP_RESEND_BLOCKED (FA-15). "
                    + "Limit: 15 requests per minute per IP. expiresIn comes from JWT_DURATION.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON,
                    schema = @Schema(implementation = LoginRequestDTO.class),
                    examples = {
                            @ExampleObject(name = "Buyer (BUYER)", value = LOGIN_COMPRADOR),
                            @ExampleObject(name = "Store (STORE_ADMIN)", value = LOGIN_TIENDA),
                            @ExampleObject(name = "Foundation (FOUNDATION_REPRESENTATIVE)", value = LOGIN_FUNDACION),
                            @ExampleObject(name = "Admin (STYLERADAR_ADMIN)", value = LOGIN_ADMIN)
                    })))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session started or OTP code sent", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = LoginResponseDTO.class), examples = {
                            @ExampleObject(name = "EmailVerified", summary = "Logs in without code", value = SESION_CON_TOKEN),
                            @ExampleObject(name = "EmailNotVerified", summary = "OTP code sent", value = SESION_PENDIENTE_OTP)
                    })),
            @ApiResponse(responseCode = "400", description = "INVALID_DATA (FA-01 to FA-05)", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "INVALID_DATA", value = ERROR_DATOS_INVALIDOS))),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS (FA-06, RN-04)", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "INVALID_CREDENTIALS", value = ERROR_CREDENCIALES))),
            @ApiResponse(responseCode = "403", description = "ACCOUNT_NOT_ACTIVE (FA-07)", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "ACCOUNT_NOT_ACTIVE", value = ERROR_CUENTA_NO_ACTIVA))),
            @ApiResponse(responseCode = "429", description = "OTP_RESEND_BLOCKED (FA-15) or RATE_LIMIT_EXCEEDED (FA-08)",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait", schema = @Schema(type = "integer")),
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class), examples = {
                            @ExampleObject(name = "OTP_RESEND_BLOCKED", value = ERROR_REENVIO_BLOQUEADO),
                            @ExampleObject(name = "RATE_LIMIT_EXCEEDED", value = ERROR_LIMITE)
                    })),
            @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: ms-usuarios, SMTP or Redis not responding (FA-09)",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "SERVICE_UNAVAILABLE", value = ERROR_NO_DISPONIBLE)))
    })
    ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request);

    @Operation(
            summary = "Verify the email with the OTP code",
            description = "Validates the code (6 digits, valid for 300 s, at most 3 attempts). When correct it closes the verification, "
                    + "marks the email as verified in ms-usuarios (it is never asked again at login) and returns the JWT. "
                    + "On the third failure it voids only the code and starts a 60 s wait before a new one can be requested.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = VerificarOtpRequestDTO.class),
                    examples = @ExampleObject(name = "Code", value = "{\"email\":\"buyer@styleradar.com\",\"otpCode\":\"482915\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Email verified and session issued", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = LoginResponseDTO.class),
                    examples = @ExampleObject(name = "EmailVerified", value = SESION_CON_TOKEN))),
            @ApiResponse(responseCode = "400", description = "INVALID_DATA: empty code or not exactly 6 digits", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "OTP_INCORRECT (with attemptsRemaining), OTP_EXPIRED or OTP_SESSION_NOT_FOUND",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class), examples = {
                            @ExampleObject(name = "OTP_INCORRECT", value = ERROR_OTP_INCORRECTO),
                            @ExampleObject(name = "OTP_EXPIRED", value = ERROR_OTP_VENCIDO),
                            @ExampleObject(name = "OTP_SESSION_NOT_FOUND", value = ERROR_SESION_INEXISTENTE)
                    })),
            @ApiResponse(responseCode = "429", description = "OTP_ATTEMPTS_EXHAUSTED (retryAfterSeconds=60) or RATE_LIMIT_EXCEEDED",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait", schema = @Schema(type = "integer")),
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "OTP_ATTEMPTS_EXHAUSTED", value = ERROR_OTP_AGOTADO))),
            @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: Redis not responding", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> verificarOtp(@Valid @RequestBody VerificarOtpRequestDTO request);

    @Operation(
            summary = "Request a new OTP code",
            description = "Creates a new code within the ongoing verification (15 min) and voids the previous one. "
                    + "At most 3 resends per verification; each resend restarts the 300 s and the 3 attempts. "
                    + "Blocked during the 60 s wait after 3 failures.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = ReenviarOtpRequestDTO.class),
                    examples = @ExampleObject(name = "Resend", value = "{\"email\":\"buyer@styleradar.com\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New code sent", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = OtpMensajeResponseDTO.class),
                    examples = @ExampleObject(name = "CodeResent", value = OTP_REENVIADO))),
            @ApiResponse(responseCode = "401", description = "OTP_SESSION_NOT_FOUND: no ongoing verification or it expired",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "OTP_SESSION_NOT_FOUND", value = ERROR_SESION_INEXISTENTE))),
            @ApiResponse(responseCode = "429", description = "OTP_RESEND_BLOCKED, RESENDS_EXHAUSTED or RATE_LIMIT_EXCEEDED",
                    headers = @Header(name = "Retry-After", description = "Seconds to wait", schema = @Schema(type = "integer")),
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class), examples = {
                            @ExampleObject(name = "OTP_RESEND_BLOCKED", value = ERROR_REENVIO_BLOQUEADO),
                            @ExampleObject(name = "RESENDS_EXHAUSTED", value = ERROR_REENVIOS_AGOTADOS)
                    })),
            @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: mail server or Redis not responding", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<OtpMensajeResponseDTO> reenviarOtp(@Valid @RequestBody ReenviarOtpRequestDTO request);

    @Operation(
            summary = "Refresh the access token (rotation)",
            description = "Receives a valid JWT, revokes it in the Redis blacklist for its remaining lifetime and returns a new one. "
                    + "Rejects tokens revoked by logout or by an account block (ms-usuarios event). "
                    + "Only works before the token expires.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = RefreshTokenRequestDTO.class),
                    examples = @ExampleObject(name = "Refresh", value = "{\"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiJ9.signature\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token rotated", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_TOKEN", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "INVALID_TOKEN", value = ERROR_TOKEN_INVALIDO)))
    })
    ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request);

    @Operation(
            summary = "Log out and revoke the token",
            description = "Adds the JWT to the Redis blacklist until it expires. The front end closes the local session "
                    + "even if this call fails.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session closed and token revoked"),
            @ApiResponse(responseCode = "401", description = "INVALID_TOKEN: Authorization missing, expired or revoked",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "503", description = "SERVICE_UNAVAILABLE: Redis not responding",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader);
}
