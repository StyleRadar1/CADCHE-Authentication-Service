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
        name = "Autenticación con Google (OAuth2 / OIDC)",
        description = "Autenticación federada stateless con Google Identity Services mediante ID Token (RS256/JWKS) "
                + "o flujo Authorization Code con protección anti-CSRF state en Redis y emisión de JWT de StyleRadar")
public interface GoogleOAuth2Api {

    @PostMapping
    @Operation(
            summary = "Iniciar sesión con ID Token de Google (Sign-In / One Tap)",
            description = "Verifica criptográficamente un ID Token RS256 emitido por Google Identity contra Google JWKS, "
                    + "valida emisor (accounts.google.com), audiencia (GOOGLE_CLIENT_ID), vigencia temporal y correo verificado. "
                    + "Resuelve o aprovisiona al usuario en ms-usuarios y emite directamente el JWT Bearer de StyleRadar sin desafío OTP adicional.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GoogleLoginRequestDTO.class),
                            examples = @ExampleObject(
                                    name = "GoogleIdToken",
                                    summary = "ID Token OIDC emitido por Google Sign-In en frontend/móvil",
                                    value = "{\"idToken\":\"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMDk4NzY1NDMyMSIsImVtYWlsIjoidXN1YXJpb0BnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZX0.firma\"}"))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "ID Token válido y cuenta activa; retorna JWT Bearer emitido por StyleRadar",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "LoginGoogleExitoso",
                                    value = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIyNSIsInJvbGVzIjpbIkNPTVBSQURPUiJdfQ.firma\",\"tokenType\":\"Bearer\",\"expiresIn\":86400,\"usuarioId\":25,\"roles\":[\"COMPRADOR\"],\"requiereOtp\":false,\"otpExpiraEnSegundos\":0,\"intentosOtpRestantes\":0}"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Solicitud inválida por ID Token faltante o formato incorrecto",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "ID Token de Google inválido, expirado, con firma/audiencia inválida o correo sin verificar",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Cuenta en StyleRadar inactiva o bloqueada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "429",
                    description = "Límite de tasa excedido por ventana de tiempo",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "GOOGLE_CLIENT_ID no configurado o dependencia crítica externa (Google JWKS / ms-usuarios) no disponible",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> autenticarConGoogle(@Valid @RequestBody GoogleLoginRequestDTO request);

    @GetMapping("/url")
    @Operation(
            summary = "Generar URL de autorización de Google OAuth2 (con state anti-CSRF)",
            description = "Genera una URL de redirección a Google Accounts con alcance openid email profile y un token state "
                    + "criptográfico de un solo uso almacenado en Redis (TTL 300s). Permite probar el flujo OAuth2 completo desde Swagger o frontend.",
            security = {})
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "URL de autorización y state anti-CSRF generados correctamente",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GoogleAuthUrlResponseDTO.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "La redirectUri solicitada no está incluida en GOOGLE_ALLOWED_REDIRECT_URIS (prevención Open Redirect)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "GOOGLE_CLIENT_ID no configurado o Redis no disponible (fail-closed)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<GoogleAuthUrlResponseDTO> obtenerUrlAutorizacion(
            @Parameter(
                    description = "URI de redirección permitida tras el consentimiento de Google (opcional; usa la configurada por defecto si se omite)",
                    example = "http://localhost:8100/api/v1/auth/oauth2/google/callback")
            @RequestParam(value = "redirectUri", required = false) String redirectUri);

    @GetMapping("/callback")
    @Operation(
            summary = "Callback OAuth2 de Google (GET redirect_uri)",
            description = "Endpoint receptor del redirect de Google tras autorizar el inicio de sesión. "
                    + "Valida de un solo uso el state anti-CSRF en Redis, intercambia el authorization code por un ID Token en Google Token Endpoint, "
                    + "verifica el ID Token criptográficamente y retorna el JWT Bearer de StyleRadar.",
            security = {})
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Autenticación con Google completada; retorna JWT Bearer de StyleRadar",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Parámetros code/state faltantes o redirectUri no autorizada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Parámetro state inválido/expirado/reutilizado, acceso denegado en Google o authorization code inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Cuenta de StyleRadar inactiva o bloqueada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "Credenciales Google OAuth2 no configuradas o falla de conectividad con Google/Redis/ms-usuarios",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> callbackGoogle(
            @Parameter(description = "Authorization code emitido por Google")
            @RequestParam(value = "code", required = false) String code,
            @Parameter(description = "Token state anti-CSRF emitido previamente por /url")
            @RequestParam(value = "state", required = false) String state,
            @Parameter(description = "URI de redirección opcional cuando difiere del valor guardado")
            @RequestParam(value = "redirectUri", required = false) String redirectUri,
            @Parameter(description = "Código de error devuelto por Google si el usuario canceló la autorización")
            @RequestParam(value = "error", required = false) String error);

    @PostMapping("/code")
    @Operation(
            summary = "Intercambiar Authorization Code de Google vía JSON POST",
            description = "Alternativa JSON SPA/Mobile al callback GET para enviar el authorization code y state obtenidos tras Google Consent "
                    + "e intercambiarlos en el backend por un JWT Bearer de StyleRadar.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GoogleCodeLoginRequestDTO.class))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authorization code intercambiado y validado; retorna JWT Bearer de StyleRadar",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Datos faltantes o redirectUri no autorizada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "State anti-CSRF inválido/expirado/reutilizado o código rechazado por Google",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "403",
                    description = "Cuenta de StyleRadar inactiva o bloqueada",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "503",
                    description = "Credenciales Google OAuth2 no configuradas o dependencia externa no disponible",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> autenticarConCodigo(@Valid @RequestBody GoogleCodeLoginRequestDTO request);
}

