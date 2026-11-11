package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;

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
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;
import edu.dosw.styleradar.auth.infrastructure.config.SecurityConfig;
import edu.dosw.styleradar.auth.infrastructure.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
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

    @Test
    @DisplayName("Login exitoso debe validar credenciales, generar OTP y retornar requiereOtp=true con 200 OK")
    void login_conCredencialesValidas_debeRetornar200YDesafioOtp() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("comprador@styleradar.com", "Password123*");

        when(usuarioClient.verificarCredenciales("comprador@styleradar.com", "Password123*"))
                .thenReturn(new VerificarCredencialesResponseDTO(10L, "comprador@styleradar.com", "COMPRADOR", "ACTIVA"));
        when(otpService.generarOtp("comprador@styleradar.com", 10L, "COMPRADOR"))
                .thenReturn("123456");
        doNothing().when(emailService).enviarOtp("comprador@styleradar.com", "123456");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requiereOtp", is(true)))
                .andExpect(jsonPath("$.usuarioId", is(10)))
                .andExpect(jsonPath("$.roles[0]", is("COMPRADOR")))
                .andExpect(jsonPath("$.otpExpiraEnSegundos", is(300)))
                .andExpect(jsonPath("$.intentosOtpRestantes", is(3)));

        verify(emailService).enviarOtp("comprador@styleradar.com", "123456");
    }

    @Test
    @DisplayName("Login con credenciales incorrectas debe retornar 401 Unauthorized")
    void login_conCredencialesIncorrectas_debeRetornar401() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("comprador@styleradar.com", "Erronea123*");

        when(usuarioClient.verificarCredenciales(anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, null, null));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("incorrectos")));
    }

    @Test
    @DisplayName("Login con cuenta inactiva o bloqueada debe retornar 403 Forbidden")
    void login_conCuentaInactiva_debeRetornar403() throws Exception {
        LoginRequestDTO request = new LoginRequestDTO("bloqueado@styleradar.com", "Password123*");

        when(usuarioClient.verificarCredenciales(anyString(), anyString()))
                .thenThrow(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, null, null));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status", is(403)))
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
                .andExpect(jsonPath("$.status", is(400)));
    }

    @Test
    @DisplayName("Verificar OTP correcto dentro del tiempo debe emitir JWT y retornar 200 OK")
    void verificarOtp_conCodigoCorrecto_debeRetornar200YTokenJwt() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "482915");

        when(otpService.obtenerUsuarioId("comprador@styleradar.com")).thenReturn(10L);
        when(otpService.obtenerRol("comprador@styleradar.com")).thenReturn("COMPRADOR");
        doNothing().when(otpService).verificarOtp("comprador@styleradar.com", "482915");
        when(jwtService.generarToken(eq(10L), anySet())).thenReturn("jwt.token.firmado");

        mockMvc.perform(post("/api/v1/auth/otp/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("jwt.token.firmado")))
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.requiereOtp", is(false)))
                .andExpect(jsonPath("$.usuarioId", is(10)));

        verify(otpService).eliminarOtp("comprador@styleradar.com");
    }

    @Test
    @DisplayName("Verificar OTP incorrecto con intentos disponibles debe retornar 401 Unauthorized")
    void verificarOtp_conCodigoIncorrecto_debeRetornar401() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "000000");

        doThrow(new OtpInvalidoException("El código OTP es incorrecto. Intentos restantes: 2"))
                .when(otpService).verificarOtp("comprador@styleradar.com", "000000");

        mockMvc.perform(post("/api/v1/auth/otp/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("incorrecto")));
    }

    @Test
    @DisplayName("Verificar OTP expirado debe retornar 401 Unauthorized")
    void verificarOtp_conCodigoExpirado_debeRetornar401() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "123456");

        doThrow(new OtpExpiradoException("El código OTP expiró o no existe. Debe solicitar uno nuevo."))
                .when(otpService).verificarOtp("comprador@styleradar.com", "123456");

        mockMvc.perform(post("/api/v1/auth/otp/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", containsString("expiró")));
    }

    @Test
    @DisplayName("Verificar OTP al tercer fallo consecutivo debe retornar 429 Too Many Requests")
    void verificarOtp_conIntentosAgotados_debeRetornar429() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "999999");

        doThrow(new OtpIntentosAgotadosException("Se agotaron los intentos disponibles para verificar el OTP. Debe solicitar un nuevo código."))
                .when(otpService).verificarOtp("comprador@styleradar.com", "999999");

        mockMvc.perform(post("/api/v1/auth/otp/verificar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status", is(429)))
                .andExpect(jsonPath("$.message", containsString("agotaron los intentos")));
    }

    @Test
    @DisplayName("Verificar OTP con formato menor a 6 dígitos debe retornar 400 Bad Request")
    void verificarOtp_conFormatoInvalido_debeRetornar400() throws Exception {
        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO("comprador@styleradar.com", "123");

        mockMvc.perform(post("/api/v1/auth/otp/verificar")
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

        mockMvc.perform(post("/api/v1/auth/otp/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is("comprador@styleradar.com")))
                .andExpect(jsonPath("$.expiraEnSegundos", is(300)))
                .andExpect(jsonPath("$.intentosRestantes", is(3)));

        verify(emailService).enviarOtp("comprador@styleradar.com", "654321");
    }

    @Test
    @DisplayName("Reenviar OTP sin sesión activa previa debe retornar 401 Unauthorized")
    void reenviarOtp_sinSesionActiva_debeRetornar401() throws Exception {
        ReenviarOtpRequestDTO request = new ReenviarOtpRequestDTO("inactivo@styleradar.com");

        when(otpService.reenviarOtp("inactivo@styleradar.com"))
                .thenThrow(new OtpExpiradoException("No se encontró una sesión activa o el tiempo de espera expiró."));

        mockMvc.perform(post("/api/v1/auth/otp/reenviar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)));
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
        when(jwtService.obtenerRoles("token.valido.jwt")).thenReturn(Set.of(Rol.COMPRADOR));
        when(jwtService.generarToken(eq(10L), anySet())).thenReturn("nuevo.token.jwt");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken", is("nuevo.token.jwt")))
                .andExpect(jsonPath("$.usuarioId", is(10)));
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

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre", is("Carlos Pérez")))
                .andExpect(jsonPath("$.email", is("carlos@styleradar.com")))
                .andExpect(jsonPath("$.roles[0]", is("COMPRADOR")));
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
                .thenReturn(new VerificarCredencialesResponseDTO(10L, "comprador@styleradar.com", "COMPRADOR", "ACTIVA"));
        when(otpService.generarOtp("comprador@styleradar.com", 10L, "COMPRADOR"))
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
        when(jwtService.obtenerRoles("token.antiguo.jwt")).thenReturn(Set.of(Rol.COMPRADOR));
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
        when(jwtService.obtenerRoles("token.usuario.revocado")).thenReturn(Set.of(Rol.COMPRADOR));
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
}
