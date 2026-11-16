package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import edu.dosw.styleradar.auth.application.dto.request.LoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.ReenviarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RefreshTokenRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RegistroAuthRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.VerificarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.ErrorResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.OtpMensajeResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.UsuarioAuthResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Autenticación", description = "Inicio de sesión con JWT (SR-RF-02), verificación por código OTP en Redis (SR-RF-92), registro de credenciales y revocación de tokens (SR-RNF-01)")
public interface AuthApi {

    @PostMapping("/login")
    @Operation(
            summary = "Iniciar sesión con correo y contraseña y disparar desafío OTP (SR-RF-02 / SR-RF-92)",
            description = "Autentica a un actor autorizado (COMPRADOR, ADMIN_ALMACEN, REPRESENTANTE_FUNDACION o ADMIN_STYLERADAR) "
                    + "validando correo electrónico, contraseña con BCrypt y estado de cuenta ACTIVA. "
                    + "Genera el código OTP de 6 dígitos en Redis con clave otp:{email}, TTL de 300 segundos (5 minutos) y máximo 3 intentos (SR-RF-92), "
                    + "o retorna el token JWT Bearer firmado con HS256 y expiración de 24 horas (86400s).",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequestDTO.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Comprador",
                                            summary = "Credenciales de un usuario comprador",
                                            value = "{\"email\":\"comprador@styleradar.com\",\"password\":\"StyleRadar2026*\"}"),
                                    @ExampleObject(
                                            name = "Administrador de Almacén",
                                            summary = "Credenciales de un administrador de almacén aliado",
                                            value = "{\"email\":\"tienda@almacenaliado.co\",\"password\":\"TiendaRadar2026*\"}"),
                                    @ExampleObject(
                                            name = "Representante de Fundación",
                                            summary = "Credenciales de un representante de fundación aliada",
                                            value = "{\"email\":\"representante@abrigar.org\",\"password\":\"Fundacion2026*\"}"),
                                    @ExampleObject(
                                            name = "Administrador StyleRadar",
                                            summary = "Credenciales de un administrador de plataforma",
                                            value = "{\"email\":\"admin@styleradar.com\",\"password\":\"AdminRadar2026*\"}")
                            })))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Credenciales válidas; retorna estado de desafío OTP (SR-RF-92) y datos de sesión JWT (SR-RF-02)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponseDTO.class),
                            examples = {
                                    @ExampleObject(
                                            name = "DesafioOtpIniciado",
                                            summary = "Credenciales validadas; código OTP de 6 dígitos enviado al correo (SR-RF-92)",
                                            value = "{\"accessToken\":null,\"tokenType\":\"Bearer\",\"expiresIn\":86400,\"usuarioId\":12,\"roles\":[\"COMPRADOR\"],\"requiereOtp\":true,\"otpExpiraEnSegundos\":300,\"intentosOtpRestantes\":3}"),
                                    @ExampleObject(
                                            name = "LoginExitosoConJwt",
                                            summary = "Sesión autenticada con token JWT HS256 (SR-RF-02)",
                                            value = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsInJvbGVzIjpbIkNPTVBSQURPUiJdLCJpYXQiOjE3MzA2NTkyMDAsImV4cCI6MTczMDc0NTYwMH0.firma\",\"tokenType\":\"Bearer\",\"expiresIn\":86400,\"usuarioId\":12,\"roles\":[\"COMPRADOR\"],\"requiereOtp\":false,\"otpExpiraEnSegundos\":0,\"intentosOtpRestantes\":3}")
                            })),
            @ApiResponse(
                    responseCode = "400",
                    description = "Solicitud inválida por campos faltantes o formato de correo/contraseña no válido",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "DatosInvalidos",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"La solicitud contiene datos inválidos\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{\"email\":\"El formato del correo electrónico es inválido\"}}"))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Credenciales incorrectas o cuenta en estado INACTIVA o BLOQUEADA",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "CredencialesInvalidas",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"No autorizado: credenciales o token inválidos\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{}}"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor durante el proceso de autenticación",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "ErrorInterno",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":500,\"error\":\"Internal Server Error\",\"message\":\"Ocurrió un error interno. Intente nuevamente más tarde.\",\"path\":\"/api/v1/auth/login\",\"validationErrors\":{}}")))
    })
    ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request);

    @PostMapping("/registro")
    @Operation(
            summary = "Registrar un nuevo comprador con contraseña (SR-RF-01 / SR-RF-02)",
            description = "Crea de forma atómica una nueva cuenta con rol COMPRADOR, estado ACTIVA, contraseña cifrada con BCrypt "
                    + "y registro de consentimiento expreso de Habeas Data (SR-RNF-02). No permite reclamar cuentas existentes.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegistroAuthRequestDTO.class),
                            examples = @ExampleObject(
                                    name = "RegistroComprador",
                                    value = "{\"nombre\":\"Laura Gómez\",\"email\":\"laura.gomez@styleradar.com\",\"telefono\":\"+57 300 123 4567\",\"password\":\"StyleRadar2026*\",\"aceptaHabeasData\":true}"))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Comprador creado exitosamente",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UsuarioAuthResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "CompradorCreado",
                                    value = "{\"id\":12,\"nombre\":\"Laura Gómez\",\"email\":\"laura.gomez@styleradar.com\",\"telefono\":\"+57 300 123 4567\",\"fechaRegistro\":\"2026-11-03T01:30:00Z\",\"roles\":[\"COMPRADOR\"]}"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Solicitud inválida o falta de aceptación de Habeas Data",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "SolicitudInvalida",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"La solicitud contiene datos inválidos\",\"path\":\"/api/v1/auth/registro\",\"validationErrors\":{\"password\":\"La contraseña debe tener entre 8 y 72 caracteres\"}}"))),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflicto concurrente de integridad al registrar el correo",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "ConflictoConcurrente",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":409,\"error\":\"Conflict\",\"message\":\"La operación entra en conflicto con el estado actual de los datos\",\"path\":\"/api/v1/auth/registro\",\"validationErrors\":{}}"))),
            @ApiResponse(
                    responseCode = "422",
                    description = "El correo electrónico ya se encuentra registrado en la plataforma",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "CorreoDuplicado",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":422,\"error\":\"Unprocessable Entity\",\"message\":\"El correo electrónico ya está registrado\",\"path\":\"/api/v1/auth/registro\",\"validationErrors\":{}}"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<UsuarioAuthResponseDTO> registrar(@Valid @RequestBody RegistroAuthRequestDTO request);

    @PostMapping("/otp/verificar")
    @Operation(
            summary = "Verificar inicio de sesión con código OTP de 6 dígitos (SR-RF-92)",
            description = "Valida el código de un solo uso (OTP) de 6 dígitos enviado al correo electrónico tras validar las credenciales. "
                    + "Concede el acceso emitiendo el JWT únicamente si el código se ingresa dentro de los 5 minutos siguientes (TTL 300s en Redis bajo otp:{email}) "
                    + "y en un máximo de 3 intentos consecutivos.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = VerificarOtpRequestDTO.class),
                            examples = @ExampleObject(
                                    name = "VerificarOtp",
                                    value = "{\"email\":\"comprador@styleradar.com\",\"codigoOtp\":\"482915\"}"))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Código OTP verificado correctamente; retorna token JWT Bearer definitivo",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "OtpVerificado",
                                    value = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsInJvbGVzIjpbIkNPTVBSQURPUiJdLCJpYXQiOjE3MzA2NTkyMDAsImV4cCI6MTczMDc0NTYwMH0.firma\",\"tokenType\":\"Bearer\",\"expiresIn\":86400,\"usuarioId\":12,\"roles\":[\"COMPRADOR\"]}"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Formato de correo o de código OTP inválido (debe ser de 6 dígitos numéricos)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "FormatoOtpInvalido",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":400,\"error\":\"Bad Request\",\"message\":\"La solicitud contiene datos inválidos\",\"path\":\"/api/v1/auth/otp/verificar\",\"validationErrors\":{\"codigoOtp\":\"El código OTP debe contener exactamente 6 dígitos numéricos\"}}"))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Código OTP incorrecto o vencido tras superar los 5 minutos de vigencia",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "OtpInvalidoOVencido",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"El código OTP ingresado es incorrecto o ha expirado\",\"path\":\"/api/v1/auth/otp/verificar\",\"validationErrors\":{}}"))),
            @ApiResponse(
                    responseCode = "422",
                    description = "Se agotó el máximo de 3 intentos permitidos para el código OTP actual; debe solicitar uno nuevo",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "IntentosAgotados",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":422,\"error\":\"Unprocessable Entity\",\"message\":\"Se superó el máximo de 3 intentos fallidos. Solicite un nuevo código OTP.\",\"path\":\"/api/v1/auth/otp/verificar\",\"validationErrors\":{}}"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> verificarOtp(@Valid @RequestBody VerificarOtpRequestDTO request);

    @PostMapping("/otp/reenviar")
    @Operation(
            summary = "Solicitar un nuevo código OTP de verificación (SR-RF-92)",
            description = "Genera un nuevo código OTP numérico de 6 dígitos con TTL de 300 segundos en Redis y reinicia el contador de intentos a 3 "
                    + "cuando el código anterior expiró o agotó sus intentos.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReenviarOtpRequestDTO.class),
                            examples = @ExampleObject(
                                    name = "ReenviarOtp",
                                    value = "{\"email\":\"comprador@styleradar.com\"}"))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Nuevo código OTP generado y enviado al correo electrónico registrado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OtpMensajeResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "OtpReenviado",
                                    value = "{\"email\":\"comprador@styleradar.com\",\"mensaje\":\"Se envió un código de verificación de 6 dígitos a su correo registrado\",\"expiraEnSegundos\":300,\"intentosRestantes\":3}"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Formato de correo electrónico inválido",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "404",
                    description = "No existe una cuenta activa asociada al correo electrónico indicado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "CuentaNoEncontrada",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":404,\"error\":\"Not Found\",\"message\":\"No se encontró una cuenta activa para el correo especificado\",\"path\":\"/api/v1/auth/otp/reenviar\",\"validationErrors\":{}}"))),
            @ApiResponse(
                    responseCode = "422",
                    description = "La cuenta no se encuentra en estado ACTIVA para recibir códigos de acceso",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<OtpMensajeResponseDTO> reenviarOtp(@Valid @RequestBody ReenviarOtpRequestDTO request);

    @PostMapping("/refresh")
    @Operation(
            summary = "Renovar token JWT de acceso (SR-RF-02)",
            description = "Emite un nuevo token JWT firmado verificando que el token presentado sea válido, no haya sido revocado en Redis y la cuenta continúe ACTIVA.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RefreshTokenRequestDTO.class),
                            examples = @ExampleObject(
                                    name = "RefreshToken",
                                    value = "{\"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsImlhdCI6MTczMDY1OTIwMCwiZXhwIjoxNzMwNzQ1NjAwfQ.firma\"}"))))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Token JWT renovado exitosamente",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "TokenRenovado",
                                    value = "{\"accessToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsInJvbGVzIjpbIkNPTVBSQURPUiJdLCJpYXQiOjE3MzA2NjI4MDAsImV4cCI6MTczMDc0OTIwMH0.nuevaFirma\",\"tokenType\":\"Bearer\",\"expiresIn\":86400,\"usuarioId\":12,\"roles\":[\"COMPRADOR\"]}"))),
            @ApiResponse(
                    responseCode = "400",
                    description = "Solicitud inválida por token faltante",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(
                    responseCode = "401",
                    description = "Token inválido, expirado, revocado o cuenta suspendida",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "TokenInvalido",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"El token presentado es inválido, expiró o fue revocado\",\"path\":\"/api/v1/auth/refresh\",\"validationErrors\":{}}"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request);

    @PostMapping("/logout")
    @Operation(
            summary = "Cerrar sesión y revocar token JWT en Redis (SR-RNF-01)",
            description = "Registra el token JWT actual en la lista negra de Redis por el tiempo restante hasta su expiración, impidiendo su reutilización inmediata.",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesión cerrada y token revocado exitosamente"),
            @ApiResponse(
                    responseCode = "401",
                    description = "Cabecera Authorization Bearer ausente, inválida o token previamente revocado",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(
                                    name = "NoAutorizado",
                                    value = "{\"timestamp\":\"2026-11-03T01:30:00\",\"status\":401,\"error\":\"Unauthorized\",\"message\":\"No autorizado: credenciales o token inválidos\",\"path\":\"/api/v1/auth/logout\",\"validationErrors\":{}}"))),
            @ApiResponse(
                    responseCode = "500",
                    description = "Error interno del servidor",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader);
}

