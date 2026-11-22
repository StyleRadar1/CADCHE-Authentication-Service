package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.dto.request.LoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.ReenviarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RefreshTokenRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RegistroAuthRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.VerificarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;
import edu.dosw.styleradar.auth.infrastructure.config.BeanConfig;
import edu.dosw.styleradar.auth.infrastructure.config.SecurityConfig;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioIdentidadAdapter;
import edu.dosw.styleradar.auth.application.mapper.out.AuthResponseMapperImpl;
import edu.dosw.styleradar.auth.application.usecase.RegistrarUsuarioUseCaseImpl;
import edu.dosw.styleradar.auth.domain.exception.OtpBloqueadoTemporalmenteException;
import edu.dosw.styleradar.auth.infrastructure.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, BeanConfig.class,
        UsuarioIdentidadAdapter.class, AuthResponseMapperImpl.class})
@ActiveProfiles("test")
@DisplayName("Pruebas del Controlador REST AuthController - SR-RF-02 / SR-RF-92")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UsuarioClient usuarioClient;

    @MockBean
    private OtpService otpService;

    @MockBean
    private EmailService emailService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void configurarPoliticas() {
        when(jwtService.obtenerVigenciaSegundos()).thenReturn(43200L);
        when(otpService.obtenerVigenciaOtpSegundos()).thenReturn(300L);
        when(otpService.obtenerMaxIntentos()).thenReturn(3);
        when(otpService.obtenerReenviosRestantes(anyString())).thenReturn(2);
        when(otpService.obtenerUsuarioId("comprador@styleradar.com")).thenReturn(10L);
        when(otpService.obtenerRol("comprador@styleradar.com")).thenReturn("BUYER");
    }

    @Test
    @DisplayName("Contrato 1.6: si un campo incumple varias reglas se responde la de mayor prioridad (obligatorio > longitud > formato)")
    void login_conVariasReglasIncumplidas_respondeElMensajeDelContrato() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + "a".repeat(250) + "@x.co\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("INVALID_DATA")))
                .andExpect(jsonPath("$.errors.email", is("El correo electrónico excede la longitud máxima permitida")))
                .andExpect(jsonPath("$.errors.password", is("La contraseña es obligatoria")));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"otpCode\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email", is("El correo electrónico es obligatorio")))
                .andExpect(jsonPath("$.errors.otpCode", is("El código OTP es obligatorio")));
    }

    @Test
    @DisplayName("Contrato 3.2: el correo con espacios alrededor se acepta y se normaliza (sin espacios y en minúsculas)")
    void login_conEspaciosEnElCorreo_seNormaliza() throws Exception {
        when(usuarioClient.verificarCredenciales(anyString(), eq("Password123*")))
                .thenReturn(new VerificarCredencialesResponseDTO(10L, "comprador@styleradar.com", "BUYER", "ACTIVA", true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"  Comprador@StyleRadar.com \",\"password\":\"Password123*\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("comprador@styleradar.com")))
                .andExpect(jsonPath("$.otpRequired", is(false)));
    }

    @Test
    @DisplayName("Login exitoso debe validar credenciales, generar OTP y retornar requiereOtp=true con 200 OK")
    void login_conCredencialesValidas_debeRetornar200YDesafioOtp() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("comprador@styleradar.com", "Password123*");

        when(usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*"))
                .thenReturn(new VerificarCredencialesResponseDTO(10L, "comprador@styleradar.com", "BUYER", "ACTIVA"));
        when(otpService.generarOtp("comprador@styleradar.com", 10L, "BUYER"))
                .thenReturn("123456");
        doNothing().when(emailService).enviarOtp("comprador@styleradar.com", "123456");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.otpRequired", is(true)))
                .andExpect(jsonPath("$.userId", is(10)))
                .andExpect(jsonPath("$.roles[0]", is("BUYER")))
                .andExpect(jsonPath("$.otpExpiresInSeconds", is(300)))
                .andExpect(jsonPath("$.otpAttemptsRemaining", is(3)))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.email", is("comprador@styleradar.com")))
                .andExpect(jsonPath("$.emailVerified", is(false)))
                .andExpect(jsonPath("$.expiresIn", is(43200)));

        verify(emailService).enviarOtp("comprador@styleradar.com", "123456");
    }

    @Test
    @DisplayName("Login con credenciales incorrectas debe retornar 401 Unauthorized")
    void login_conCredencialesIncorrectas_debeRetornar401() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("comprador@styleradar.com", "Erronea123*");

        when(usuarioClient.verificarCredenciales(anyString(), anyString()))
                .thenThrow(new CredencialesIncorrectasException("El correo o la contraseña son incorrectos"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("INVALID_CREDENTIALS")))
                .andExpect(jsonPath("$.message", containsString("incorrectos")));
    }

    @Test
    @DisplayName("Login con cuenta inactiva o bloqueada debe retornar 403 Forbidden")
    void login_conCuentaInactiva_debeRetornar403() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("bloqueado@styleradar.com", "Password123*");

        when(usuarioClient.verificarCredenciales(anyString(), anyString()))
                .thenThrow(new CuentaNoActivaException("La cuenta se encuentra inactiva o bloqueada"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_ACTIVE")))
                .andExpect(jsonPath("$.message", containsString("inactiva o bloqueada")));
    }

    @Test
    @DisplayName("Login con email inválido debe retornar 400 Bad Request por validación")
    void login_conEmailInvalido_debeRetornar400() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("formato-invalido", "Password123*");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.code", is("INVALID_DATA")))
                .andExpect(jsonPath("$.errors.email").exists());
    }

    @Test
    @DisplayName("Verificar OTP correcto dentro del tiempo debe emitir JWT y retornar 200 OK")
    void verificarOtp_conCodigoCorrecto_debeRetornar200YTokenJwt() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "482915");

        when(otpService.obtenerUsuarioId("comprador@styleradar.com")).thenReturn(10L);
        when(otpService.obtenerRol("comprador@styleradar.com")).thenReturn("BUYER");
        doNothing().when(otpService).verificarOtp("comprador@styleradar.com", "482915");
        when(jwtService.generarToken(eq(10L), anySet())).thenReturn("jwt.token.firmado");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("jwt.token.firmado")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.otpRequired", is(false)))
                .andExpect(jsonPath("$.userId", is(10)))
                .andExpect(jsonPath("$.email", is("comprador@styleradar.com")))
                .andExpect(jsonPath("$.emailVerified", is(true)))
                .andExpect(jsonPath("$.otpAttemptsRemaining", is(0)));

        verify(otpService).eliminarOtp("comprador@styleradar.com");
        verify(usuarioClient).marcarCorreoVerificado(10L);
    }

    @Test
    @DisplayName("Verificar OTP incorrecto con intentos disponibles debe retornar 401 Unauthorized")
    void verificarOtp_conCodigoIncorrecto_debeRetornar401() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "000000");

        doThrow(new OtpInvalidoException(2))
                .when(otpService).verificarOtp("comprador@styleradar.com", "000000");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("OTP_INCORRECT")))
                .andExpect(jsonPath("$.attemptsRemaining", is(2)))
                .andExpect(jsonPath("$.message", containsString("incorrecto")));
    }

    @Test
    @DisplayName("Verificar OTP expirado debe retornar 401 Unauthorized")
    void verificarOtp_conCodigoExpirado_debeRetornar401() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "123456");

        doThrow(new OtpExpiradoException("El código OTP expiró o no existe. Debe solicitar uno nuevo."))
                .when(otpService).verificarOtp("comprador@styleradar.com", "123456");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("OTP_EXPIRED")))
                .andExpect(jsonPath("$.message", containsString("expiró")));
    }

    @Test
    @DisplayName("Verificar OTP al tercer fallo consecutivo debe retornar 429 Too Many Requests")
    void verificarOtp_conIntentosAgotados_debeRetornar429() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "999999");

        doThrow(OtpIntentosAgotadosException.intentosAgotados(60L))
                .when(otpService).verificarOtp("comprador@styleradar.com", "999999");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.code", is("OTP_ATTEMPTS_EXHAUSTED")))
                .andExpect(jsonPath("$.attemptsRemaining", is(0)))
                .andExpect(jsonPath("$.retryAfterSeconds", is(60)))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"))
                .andExpect(jsonPath("$.message", is("Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.")));
    }

    @Test
    @DisplayName("Verificar OTP con formato menor a 6 dígitos debe retornar 400 Bad Request")
    void verificarOtp_conFormatoInvalido_debeRetornar400() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "123");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Reenviar OTP con sesión activa debe regenerar código, enviar correo y retornar 200 OK")
    void reenviarOtp_conSesionActiva_debeRetornar200() throws Exception {
        ReenviarOtpRequestDTO request = new ReenviarOtpRequestDTO("comprador@styleradar.com");

        when(otpService.reenviarOtp("comprador@styleradar.com")).thenReturn("654321");
        doNothing().when(emailService).enviarOtp("comprador@styleradar.com", "654321");

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("comprador@styleradar.com")))
                .andExpect(jsonPath("$.expiresInSeconds", is(300)))
                .andExpect(jsonPath("$.attemptsRemaining", is(3)))
                .andExpect(jsonPath("$.resendsRemaining", is(2)));

        verify(emailService).enviarOtp("comprador@styleradar.com", "654321");
    }

    @Test
    @DisplayName("Reenviar OTP sin sesión activa previa debe retornar 401 Unauthorized")
    void reenviarOtp_sinSesionActiva_debeRetornar401() throws Exception {
        ReenviarOtpRequestDTO request = new ReenviarOtpRequestDTO("inactivo@styleradar.com");

        when(otpService.reenviarOtp("inactivo@styleradar.com"))
                .thenThrow(OtpExpiradoException.sesionInexistente());

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("OTP_SESSION_NOT_FOUND")));
    }

    @Test
    @DisplayName("Refresh con token válido y no revocado debe retornar nuevo JWT con 200 OK")
    void refresh_conTokenValido_debeRetornarNuevoJwt() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("token.valido.jwt");

        Claims claims = Jwts.claims()
                .subject("10")
                .build();

        when(jwtService.validarToken("token.valido.jwt")).thenReturn(claims);
        when(otpService.esTokenInvalido("token.valido.jwt")).thenReturn(false);
        when(jwtService.obtenerUsuarioId("token.valido.jwt")).thenReturn(10L);
        when(jwtService.obtenerRoles("token.valido.jwt")).thenReturn(Set.of(Rol.BUYER));
        when(jwtService.generarToken(eq(10L), anySet())).thenReturn("nuevo.token.jwt");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("nuevo.token.jwt")))
                .andExpect(jsonPath("$.userId", is(10)));
    }

    @Test
    @DisplayName("Refresh con token revocado o inválido debe retornar 401 Unauthorized")
    void refresh_conTokenRevocado_debeRetornar401() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("token.revocado.jwt");

        Claims claims = Jwts.claims()
                .subject("10")
                .build();

        when(jwtService.validarToken("token.revocado.jwt")).thenReturn(claims);
        when(otpService.esTokenInvalido("token.revocado.jwt")).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.code", is("INVALID_TOKEN")))
                .andExpect(jsonPath("$.message", containsString("revocado")));
    }

    @Test
    @DisplayName("Logout con token válido debe revocarlo en lista negra de Redis y retornar 204 No Content")
    void logout_conTokenValido_debeRevocarYRetornar204() throws Exception {
        Claims claims = Jwts.claims()
                .subject("10")
                .issuedAt(new Date())
                .build();

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);

        when(jwtService.validarToken("token.a.revocar")).thenReturn(claims);
        when(jwtService.obtenerTiempoRestanteSegundos("token.a.revocar")).thenReturn(3600L);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer token.a.revocar"))
                .andExpect(status().isNoContent());

        verify(otpService).invalidarToken("token.a.revocar", 3600L);
    }

    @Test
    @DisplayName("Registro de comprador debe retornar 201 Created con usuarioAuthResponseDTO")
    void registro_conDatosValidos_debeRetornar201() throws Exception {
        RegistroAuthRequestDTO request = new RegistroAuthRequestDTO(
                "Carlos Pérez", "carlos@styleradar.com", "+57 300 123 4567", "StyleRadar2026*", true);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Carlos Pérez")))
                .andExpect(jsonPath("$.email", is("carlos@styleradar.com")))
                .andExpect(jsonPath("$.roles[0]", is("BUYER")));
    }

    @Test
    @DisplayName("Login cuando ms-usuarios no está disponible debe fallar cerrado con 503 Service Unavailable")
    void login_conServicioExternoNoDisponible_debeRetornar503() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("comprador@styleradar.com", "Password123*");

        when(usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*"))
                .thenThrow(new ServicioExternoNoDisponibleException("ms-usuarios caído"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)));
    }

    @Test
    @DisplayName("Login cuando falla el envío SMTP debe hacer rollback del OTP en Redis y retornar 503")
    void login_conFalloSmtp_debeHacerRollbackOtpYRetornar503() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("comprador@styleradar.com", "Password123*");

        when(usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*"))
                .thenReturn(new VerificarCredencialesResponseDTO(10L, "comprador@styleradar.com", "BUYER", "ACTIVA"));
        when(otpService.generarOtp("comprador@styleradar.com", 10L, "BUYER"))
                .thenReturn("123456");
        doThrow(new ServicioExternoNoDisponibleException("SMTP caído"))
                .when(emailService).enviarOtp("comprador@styleradar.com", "123456");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)));

        verify(otpService).eliminarOtp("comprador@styleradar.com");
    }

    @Test
    @DisplayName("Refresh válido debe rotar e invalidar el token anterior en blacklist")
    void refresh_conTiempoRestantePositivo_debeRotarTokenAnterior() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("token.antiguo.jwt");
        Claims claims = Jwts.claims()
                .subject("10")
                .issuedAt(new Date())
                .build();

        when(otpService.esTokenInvalido("token.antiguo.jwt")).thenReturn(false);
        when(jwtService.validarToken("token.antiguo.jwt")).thenReturn(claims);
        when(jwtService.obtenerUsuarioId("token.antiguo.jwt")).thenReturn(10L);
        when(jwtService.obtenerRoles("token.antiguo.jwt")).thenReturn(Set.of(Rol.BUYER));
        when(otpService.estaUsuarioRevocado(eq(10L), org.mockito.ArgumentMatchers.anyLong())).thenReturn(false);
        when(jwtService.obtenerTiempoRestanteSegundos("token.antiguo.jwt")).thenReturn(1800L);
        when(jwtService.generarToken(eq(10L), anySet())).thenReturn("token.rotado.jwt");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("token.rotado.jwt")));

        verify(otpService).invalidarToken("token.antiguo.jwt", 1800L);
    }

    @Test
    @DisplayName("Refresh de usuario revocado globalmente debe retornar 401 Unauthorized")
    void refresh_conUsuarioRevocadoGlobalmente_debeRetornar401() throws Exception {
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO("token.usuario.revocado");
        Claims claims = Jwts.claims()
                .subject("10")
                .issuedAt(new Date(1000L))
                .build();

        when(otpService.esTokenInvalido("token.usuario.revocado")).thenReturn(false);
        when(jwtService.validarToken("token.usuario.revocado")).thenReturn(claims);
        when(jwtService.obtenerUsuarioId("token.usuario.revocado")).thenReturn(10L);
        when(jwtService.obtenerRoles("token.usuario.revocado")).thenReturn(Set.of(Rol.BUYER));
        when(otpService.estaUsuarioRevocado(10L, 1000L)).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Logout sin cabecera Authorization Bearer debe retornar 401 Unauthorized")
    void logout_sinAuthorizationBearer_debeRetornar401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
    }

    @Test
    @DisplayName("Cobertura de ramas defensivas en login, verificarOtp, refresh, logout y registro fail-closed")
    void debeCubrirRamasDefensivasDelControlador() throws Exception {
        LoginRequestDTO loginReq = new LoginRequestDTO("comprador@styleradar.com", "Password123*");
        when(usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*"))
                .thenReturn(new VerificarCredencialesResponseDTO(null, "comprador@styleradar.com", "BUYER", "ACTIVA"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isServiceUnavailable());

        when(usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*"))
                .thenReturn(new VerificarCredencialesResponseDTO(10L, "comprador@styleradar.com", "ROL_INEXISTENTE", "ACTIVA"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isUnauthorized());

        VerificarOtpRequestDTO otpReq = new VerificarOtpRequestDTO("comprador@styleradar.com", "482915");
        when(otpService.obtenerUsuarioId("comprador@styleradar.com")).thenReturn(null);
        when(otpService.obtenerRol("comprador@styleradar.com")).thenReturn(null);
        doNothing().when(otpService).verificarOtp("comprador@styleradar.com", "482915");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(otpReq)))
                .andExpect(status().isUnauthorized());

        RefreshTokenRequestDTO refreshReq = new RefreshTokenRequestDTO("token.incompleto.jwt");
        Claims claims = Jwts.claims().subject("10").build();
        when(otpService.esTokenInvalido("token.incompleto.jwt")).thenReturn(false);
        when(jwtService.validarToken("token.incompleto.jwt")).thenReturn(claims);
        when(jwtService.obtenerUsuarioId("token.incompleto.jwt")).thenReturn(10L);
        when(jwtService.obtenerRoles("token.incompleto.jwt")).thenReturn(Set.of());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer    "))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code", is("INVALID_TOKEN")));

        RegistrarUsuarioUseCaseImpl registroCerrado =
                new RegistrarUsuarioUseCaseImpl(false, java.time.Clock.systemUTC());
        org.junit.jupiter.api.Assertions.assertThrows(
                ServicioExternoNoDisponibleException.class,
                () -> registroCerrado.registrar("Ana", "ana@styleradar.com", "+57 300 000 0000"));
    }

    @Test
    @DisplayName("SR-RF-02 / RN-05: login con correo verificado entrega el token de una vez, sin enviar OTP")
    void login_conCorreoVerificado_debeEntregarTokenSinOtp() throws Exception {
        when(usuarioClient.verificarCredenciales("verificado@styleradar.com", "Password123*"))
                .thenReturn(new VerificarCredencialesResponseDTO(
                        11L, "verificado@styleradar.com", "STORE_ADMIN", "ACTIVA", true));
        when(jwtService.generarToken(eq(11L), anySet())).thenReturn("jwt.directo");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestDTO("Verificado@StyleRadar.com", "Password123*"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("jwt.directo")))
                .andExpect(jsonPath("$.otpRequired", is(false)))
                .andExpect(jsonPath("$.emailVerified", is(true)))
                .andExpect(jsonPath("$.otpExpiresInSeconds").doesNotExist())
                .andExpect(jsonPath("$.otpAttemptsRemaining", is(0)))
                .andExpect(jsonPath("$.expiresIn", is(43200)))
                .andExpect(jsonPath("$.roles[0]", is("STORE_ADMIN")));

        org.mockito.Mockito.verifyNoInteractions(emailService);
        verify(otpService, org.mockito.Mockito.never()).generarOtp(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("SR-RF-02 FA-15: login con correo sin verificar durante la espera de 60 s responde 429 OTP_RESEND_BLOCKED")
    void login_durantePenalizacion_debeResponder429ConRetryAfter() throws Exception {
        when(usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*"))
                .thenReturn(new VerificarCredencialesResponseDTO(10L, "comprador@styleradar.com", "BUYER", "ACTIVA"));
        when(otpService.generarOtp("comprador@styleradar.com", 10L, "BUYER"))
                .thenThrow(new OtpBloqueadoTemporalmenteException(42L));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestDTO("comprador@styleradar.com", "Password123*"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "42"))
                .andExpect(jsonPath("$.code", is("OTP_RESEND_BLOCKED")))
                .andExpect(jsonPath("$.retryAfterSeconds", is(42)))
                .andExpect(jsonPath("$.message", is("Debe esperar 42 segundos para solicitar un nuevo código.")));

        org.mockito.Mockito.verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("RN-03: una cuenta que ms-usuarios reporta INACTIVA nunca obtiene sesión (403 ACCOUNT_NOT_ACTIVE)")
    void login_conEstadoInactivoEnRespuesta_debeResponder403() throws Exception {
        when(usuarioClient.verificarCredenciales("inactivo@styleradar.com", "Password123*"))
                .thenReturn(new VerificarCredencialesResponseDTO(
                        12L, "inactivo@styleradar.com", "BUYER", "INACTIVA", true));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequestDTO("inactivo@styleradar.com", "Password123*"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("ACCOUNT_NOT_ACTIVE")));

        org.mockito.Mockito.verifyNoInteractions(jwtService);
    }
}
