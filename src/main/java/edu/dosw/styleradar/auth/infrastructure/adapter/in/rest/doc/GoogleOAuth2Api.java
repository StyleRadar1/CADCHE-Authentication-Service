package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import edu.dosw.styleradar.auth.application.dto.request.GoogleCodeLoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.GoogleLoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.ErrorResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.GoogleAuthUrlResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(
        name = "Google sign-in (OAuth2 / OIDC)",
        description = "Stateless federated sign-in with Google Identity Services through an ID token (RS256/JWKS) "
                + "or the Authorization Code flow with an anti-CSRF state in Redis; issues the StyleRadar JWT")
public interface GoogleOAuth2Api {

    @PostMapping
    @Operation(
            summary = "Log in with a Google ID token (Sign-In / One Tap)",
            description = "Cryptographically verifies a Google RS256 ID token against Google JWKS and validates issuer (accounts.google.com), "
                    + "audience (GOOGLE_CLIENT_ID), lifetime and verified email. "
                    + "Resolves or provisions the user in ms-usuarios and returns the StyleRadar JWT without OTP (RN-06).",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GoogleLoginRequestDTO.class),
                            examples = @ExampleObject(
                                    name = "GoogleIdToken",
                                    summary = "OIDC ID token issued by Google Sign-In on web or mobile",
                                    value = "{\"idToken\":\"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMDk4NzY1NDMyMSIsImVtYWlsIjoidXN1YXJpb0BnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZX0.firma\"}"))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Valid ID token and active account; returns the StyleRadar JWT without OTP. ms-auth marks the email as verified in ms-usuarios (FA-10)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "GoogleLoginOk",
                                    value = AuthApiEjemplos.SESION_CON_TOKEN))),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_DATA: missing ID token",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "INVALID_CREDENTIALS: Google ID token invalid, expired, wrong signature/audience or unverified email (FA-11)",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "ACCOUNT_NOT_ACTIVE: StyleRadar account inactive or blocked (FA-12)",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "RATE_LIMIT_EXCEEDED",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "SERVICE_UNAVAILABLE: GOOGLE_CLIENT_ID not configured, or Google JWKS / ms-usuarios not responding (FA-14)",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> autenticarConGoogle(@Valid @RequestBody GoogleLoginRequestDTO request);

    @GetMapping("/url")
    @Operation(
            summary = "Build the Google OAuth2 authorization URL (anti-CSRF state)",
            description = "Builds a Google Accounts redirect URL with scope openid email profile and a single-use cryptographic state "
                    + "stored in Redis (TTL 300 s). Returns authorizationUrl, state and redirectUri.",
            security = {})
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authorization URL and anti-CSRF state generated",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GoogleAuthUrlResponseDTO.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_DATA: redirectUri is not in GOOGLE_ALLOWED_REDIRECT_URIS (open-redirect protection)",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "SERVICE_UNAVAILABLE: GOOGLE_CLIENT_ID not configured or Redis not responding (fail-closed)",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<GoogleAuthUrlResponseDTO> obtenerUrlAutorizacion(
            @Parameter(
                    description = "Allowed redirect URI after Google consent (optional; the configured default is used when omitted)",
                    example = "http://localhost:8100/api/v1/auth/oauth2/google/callback")
            @RequestParam(value = "redirectUri", required = false) String redirectUri);

    @GetMapping("/callback")
    @Operation(
            summary = "Google OAuth2 callback (GET redirect_uri)",
            description = "Receives the Google redirect after consent. "
                    + "Consumes the single-use anti-CSRF state in Redis, exchanges the authorization code for an ID token at the Google token endpoint, "
                    + "verifies the ID token and returns the StyleRadar JWT.",
            security = {})
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Google sign-in completed; returns the StyleRadar JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_DATA: missing code/state or redirectUri not allowed",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "INVALID_CREDENTIALS: invalid, expired or reused state, access denied at Google or invalid authorization code",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "ACCOUNT_NOT_ACTIVE: StyleRadar account inactive or blocked",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "SERVICE_UNAVAILABLE: Google OAuth2 not configured or Google/Redis/ms-usuarios not responding",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> callbackGoogle(
            @Parameter(description = "Authorization code issued by Google")
            @RequestParam(value = "code", required = false) String code,
            @Parameter(description = "Anti-CSRF state previously issued by /url")
            @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "Optional redirect URI when it differs from the stored one")
            @RequestParam(value = "redirectUri", required = false) String redirectUri,
            @Parameter(description = "Error code returned by Google when the user cancels")
            @RequestParam(value = "error", required = false) String error);

    @PostMapping("/code")
    @Operation(
            summary = "Exchange a Google authorization code (JSON POST)",
            description = "JSON alternative to the GET callback for SPA/mobile clients: sends the authorization code and state obtained "
                    + "after Google consent and exchanges them for a StyleRadar JWT.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GoogleCodeLoginRequestDTO.class))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authorization code exchanged; returns the StyleRadar JWT",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "INVALID_DATA: missing data or redirectUri not allowed",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "INVALID_CREDENTIALS: invalid, expired or reused state, or code rejected by Google",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "ACCOUNT_NOT_ACTIVE: StyleRadar account inactive or blocked",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "SERVICE_UNAVAILABLE: Google OAuth2 not configured or an external dependency is down",
                    content = @Content(mediaType = ErrorResponseDTO.MEDIA_TYPE, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> autenticarConCodigo(@Valid @RequestBody GoogleCodeLoginRequestDTO request);
}

