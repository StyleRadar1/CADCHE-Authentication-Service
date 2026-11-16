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
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.REGISTRO;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.SESION_CON_TOKEN;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.SESION_PENDIENTE_OTP;
import static edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApiEjemplos.USUARIO_REGISTRADO;

import org.springframework.http.ResponseEntity;
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
 * Contrato OpenAPI de ms-auth (contrato de integración con el Frontend v1.2). Todos los errores siguen
 * {@link ErrorResponseDTO}: el front elige el texto por {@code codigo} (decisión D3) y todos los 429
 * traen {@code esperarSegundos} y la cabecera {@code Retry-After}.
 */
@Tag(name = "Autenticación", description = "Inicio de sesión (SR-RF-02), verificación del correo con código OTP una sola vez (SR-RF-92), renovación y cierre de sesión con revocación en Redis 7")
public interface AuthApi {

    String JSON = "application/json";
    String PROBLEMA = ErrorResponseDTO.MEDIA_TYPE;

    @Operation(
            summary = "Iniciar sesión con correo y contraseña (SR-RF-02)",
            description = "Valida las credenciales contra ms-usuarios (8120) y exige cuenta ACTIVA (RN-03). "
                    + "Si el correo ya está verificado entrega el JWT de una vez (RN-05, requiereOtp=false). "
                    + "Si no, envía un código OTP de 6 dígitos (300 s, 3 intentos) y responde requiereOtp=true sin token (FA-13). "
                    + "Durante la espera de 60 s tras 3 fallos responde 429 OTP_REENVIO_BLOQUEADO (FA-15). "
                    + "Límite de 15 solicitudes por minuto por IP. expiresIn sale de JWT_DURATION.",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON,
                    schema = @Schema(implementation = LoginRequestDTO.class),
                    examples = {
                            @ExampleObject(name = "Comprador", value = LOGIN_COMPRADOR),
                            @ExampleObject(name = "Tienda (ADMIN_ALMACEN)", value = LOGIN_TIENDA),
                            @ExampleObject(name = "Fundación (REPRESENTANTE_FUNDACION)", value = LOGIN_FUNDACION),
                            @ExampleObject(name = "Admin (ADMIN_STYLERADAR)", value = LOGIN_ADMIN)
                    })))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sesión iniciada o código OTP enviado", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = LoginResponseDTO.class), examples = {
                            @ExampleObject(name = "CorreoVerificado", summary = "Entra sin código", value = SESION_CON_TOKEN),
                            @ExampleObject(name = "CorreoSinVerificar", summary = "Se envió el código OTP", value = SESION_PENDIENTE_OTP)
                    })),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS (FA-01 a FA-05)", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "DATOS_INVALIDOS", value = ERROR_DATOS_INVALIDOS))),
            @ApiResponse(responseCode = "401", description = "CREDENCIALES_INCORRECTAS (FA-06, RN-04)", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "CREDENCIALES_INCORRECTAS", value = ERROR_CREDENCIALES))),
            @ApiResponse(responseCode = "403", description = "CUENTA_NO_ACTIVA (FA-07)", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "CUENTA_NO_ACTIVA", value = ERROR_CUENTA_NO_ACTIVA))),
            @ApiResponse(responseCode = "429", description = "OTP_REENVIO_BLOQUEADO (FA-15) o LIMITE_SOLICITUDES (FA-08)",
                    headers = @Header(name = "Retry-After", description = "Segundos de espera", schema = @Schema(type = "integer")),
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class), examples = {
                            @ExampleObject(name = "OTP_REENVIO_BLOQUEADO", value = ERROR_REENVIO_BLOQUEADO),
                            @ExampleObject(name = "LIMITE_SOLICITUDES", value = ERROR_LIMITE)
                    })),
            @ApiResponse(responseCode = "503", description = "SERVICIO_NO_DISPONIBLE: ms-usuarios, SMTP o Redis no responden (FA-09)",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "SERVICIO_NO_DISPONIBLE", value = ERROR_NO_DISPONIBLE)))
    })
    ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request);

    @Operation(
            summary = "Verificar el correo con el código OTP (SR-RF-92)",
            description = "Valida el código (6 dígitos, vigente 300 s, máximo 3 intentos). Si es correcto anula la verificación, "
                    + "marca el correo como verificado en ms-usuarios (no se vuelve a pedir en el login, D2) y entrega el JWT. "
                    + "Al tercer fallo anula solo el código y activa una espera de 60 s para pedir otro (D1).",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = VerificarOtpRequestDTO.class),
                    examples = @ExampleObject(name = "Codigo", value = "{\"email\":\"comprador@styleradar.com\",\"codigoOtp\":\"482915\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Correo verificado y sesión emitida", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = LoginResponseDTO.class),
                    examples = @ExampleObject(name = "CorreoVerificado", value = SESION_CON_TOKEN))),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS: código vacío o sin 6 dígitos", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "OTP_INCORRECTO (con intentosRestantes), OTP_VENCIDO o SESION_OTP_INEXISTENTE",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class), examples = {
                            @ExampleObject(name = "OTP_INCORRECTO", value = ERROR_OTP_INCORRECTO),
                            @ExampleObject(name = "OTP_VENCIDO", value = ERROR_OTP_VENCIDO),
                            @ExampleObject(name = "SESION_OTP_INEXISTENTE", value = ERROR_SESION_INEXISTENTE)
                    })),
            @ApiResponse(responseCode = "429", description = "OTP_INTENTOS_AGOTADOS (esperarSegundos=60) o LIMITE_SOLICITUDES",
                    headers = @Header(name = "Retry-After", description = "Segundos de espera", schema = @Schema(type = "integer")),
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "OTP_INTENTOS_AGOTADOS", value = ERROR_OTP_AGOTADO))),
            @ApiResponse(responseCode = "503", description = "SERVICIO_NO_DISPONIBLE: Redis no responde", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<LoginResponseDTO> verificarOtp(@Valid @RequestBody VerificarOtpRequestDTO request);

    @Operation(
            summary = "Pedir otro código OTP (SR-RF-92)",
            description = "Genera un código nuevo dentro de la verificación en curso (15 min) y anula el anterior. "
                    + "Máximo 3 reenvíos por verificación; cada reenvío reinicia los 300 s y los 3 intentos. "
                    + "Bloqueado durante la espera de 60 s tras 3 fallos (D1).",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = ReenviarOtpRequestDTO.class),
                    examples = @ExampleObject(name = "Reenvio", value = "{\"email\":\"comprador@styleradar.com\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Código nuevo enviado", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = OtpMensajeResponseDTO.class),
                    examples = @ExampleObject(name = "CodigoReenviado", value = OTP_REENVIADO))),
            @ApiResponse(responseCode = "401", description = "SESION_OTP_INEXISTENTE: no hay verificación en curso o venció",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                            examples = @ExampleObject(name = "SESION_OTP_INEXISTENTE", value = ERROR_SESION_INEXISTENTE))),
            @ApiResponse(responseCode = "429", description = "OTP_REENVIO_BLOQUEADO, REENVIOS_AGOTADOS o LIMITE_SOLICITUDES",
                    headers = @Header(name = "Retry-After", description = "Segundos de espera", schema = @Schema(type = "integer")),
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class), examples = {
                            @ExampleObject(name = "OTP_REENVIO_BLOQUEADO", value = ERROR_REENVIO_BLOQUEADO),
                            @ExampleObject(name = "REENVIOS_AGOTADOS", value = ERROR_REENVIOS_AGOTADOS)
                    })),
            @ApiResponse(responseCode = "503", description = "SERVICIO_NO_DISPONIBLE: correo o Redis no responden", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<OtpMensajeResponseDTO> reenviarOtp(@Valid @RequestBody ReenviarOtpRequestDTO request);

    @Operation(
            summary = "Renovar el token de acceso con rotación",
            description = "Recibe un JWT vigente, lo revoca en la lista negra de Redis por su tiempo restante y entrega uno nuevo. "
                    + "Rechaza tokens revocados por logout o por bloqueo global de la cuenta (evento de ms-usuarios). "
                    + "Solo funciona antes de que el token venza (contrato v1.2, sección 5.3).",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = RefreshTokenRequestDTO.class),
                    examples = @ExampleObject(name = "Refresh", value = "{\"refreshToken\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiJ9.firma\"}"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token rotado", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = LoginResponseDTO.class))),
            @ApiResponse(responseCode = "401", description = "TOKEN_INVALIDO", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class),
                    examples = @ExampleObject(name = "TOKEN_INVALIDO", value = ERROR_TOKEN_INVALIDO)))
    })
    ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request);

    @Operation(
            summary = "Cerrar sesión y revocar el token",
            description = "Registra el JWT en la lista negra de Redis hasta su vencimiento. El front cierra la sesión local "
                    + "aunque esta llamada falle (contrato v1.2, sección 5).",
            security = {@SecurityRequirement(name = "bearerAuth")})
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesión cerrada y token revocado"),
            @ApiResponse(responseCode = "401", description = "TOKEN_INVALIDO: Authorization ausente, vencido o revocado",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "503", description = "SERVICIO_NO_DISPONIBLE: Redis no responde",
                    content = @Content(mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<Void> logout(
            @Parameter(hidden = true) @RequestHeader(value = "Authorization", required = false) String authorizationHeader);

    @Operation(
            summary = "Registro de soporte (solo dev/test)",
            description = "Endpoint de apoyo para pruebas locales: devuelve un usuario COMPRADOR simulado. El registro real lo hace "
                    + "ms-usuarios (Database-per-Service). En QA y PROD responde siempre 503 SERVICIO_NO_DISPONIBLE (fail-closed).",
            security = {},
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = RegistroAuthRequestDTO.class),
                    examples = @ExampleObject(name = "Registro", value = REGISTRO))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario de soporte creado (dev/test)", content = @Content(
                    mediaType = JSON, schema = @Schema(implementation = UsuarioAuthResponseDTO.class),
                    examples = @ExampleObject(name = "UsuarioRegistrado", value = USUARIO_REGISTRADO))),
            @ApiResponse(responseCode = "400", description = "DATOS_INVALIDOS", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class))),
            @ApiResponse(responseCode = "503", description = "SERVICIO_NO_DISPONIBLE en QA y PROD", content = @Content(
                    mediaType = PROBLEMA, schema = @Schema(implementation = ErrorResponseDTO.class)))
    })
    ResponseEntity<UsuarioAuthResponseDTO> registrar(@Valid @RequestBody RegistroAuthRequestDTO request);
}
