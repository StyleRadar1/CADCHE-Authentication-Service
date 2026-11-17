package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.dto.request.LoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.ReenviarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.VerificarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.application.service.impl.OtpServiceImpl;
import edu.dosw.styleradar.auth.domain.exception.OtpBloqueadoTemporalmenteException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;
import edu.dosw.styleradar.auth.infrastructure.config.SecurityConfig;
import edu.dosw.styleradar.auth.infrastructure.exception.GlobalExceptionHandler;
import edu.dosw.styleradar.auth.infrastructure.config.BeanConfig;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioIdentidadAdapter;
import edu.dosw.styleradar.auth.application.mapper.out.AuthResponseMapperImpl;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, BeanConfig.class,
        UsuarioIdentidadAdapter.class, AuthResponseMapperImpl.class})
@ActiveProfiles("test")
@DisplayName("Auditoría de Requerimientos Completos - ms-auth (SR-RF-02 y SR-RF-92)")
class RequerimientosCompletosAuditoriaAuthTest {

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
    private StringRedisTemplate redisTemplate;

    private static final String EMAIL = "comprador@styleradar.com";
    private static final String PASS = "Password123*";

    @org.junit.jupiter.api.BeforeEach
    void configurarPoliticas() {
        when(jwtService.obtenerVigenciaSegundos()).thenReturn(86400L);
        when(otpService.obtenerVigenciaOtpSegundos()).thenReturn(300L);
        when(otpService.obtenerMaxIntentos()).thenReturn(3);
    }

    @Test
    @DisplayName("Prueba 1 (SR-RF-02 Flujo Básico): Login con correo verificado emite JWT directo sin enviar OTP ni pedir código")
    void prueba1_SRRF02_LoginCorreoVerificado_EntregaJwtDirectoSinOtp() throws Exception {
        VerificarCredencialesResponseDTO usuarioVerificado = new VerificarCredencialesResponseDTO(
                15L, EMAIL, "COMPRADOR", "ACTIVA", true);

        when(usuarioClient.verificarCredenciales(EMAIL, PASS)).thenReturn(usuarioVerificado);
        when(jwtService.generarToken(eq(15L), anySet())).thenReturn("styleradar.jwt.token.directo");

        LoginRequestDTO request = new LoginRequestDTO(EMAIL, PASS);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("styleradar.jwt.token.directo"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.usuarioId").value(15))
                .andExpect(jsonPath("$.correoVerificado").value(true))
                .andExpect(jsonPath("$.verificacionPendiente").value(false))
                .andExpect(jsonPath("$.requiereOtp").value(false));

        // Verificar que NO se generó ni se envió código OTP
        verify(emailService, never()).enviarOtp(anyString(), anyString());
    }

    @Test
    @DisplayName("Prueba 2 (SR-RF-02 FA-13): Login con correo no verificado retiene el JWT, envía OTP y retorna verificacionPendiente=true")
    void prueba2_SRRF02_LoginCorreoNoVerificado_ExigeOtpYRetieneJwt() throws Exception {
        VerificarCredencialesResponseDTO usuarioPendiente = new VerificarCredencialesResponseDTO(
                20L, EMAIL, "COMPRADOR", "ACTIVA", false);

        when(usuarioClient.verificarCredenciales(EMAIL, PASS)).thenReturn(usuarioPendiente);
        when(otpService.generarOtp(eq(EMAIL), eq(20L), eq("COMPRADOR"))).thenReturn("789123");

        LoginRequestDTO request = new LoginRequestDTO(EMAIL, PASS);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value(nullValue()))
                .andExpect(jsonPath("$.correoVerificado").value(false))
                .andExpect(jsonPath("$.verificacionPendiente").value(true))
                .andExpect(jsonPath("$.requiereOtp").value(true))
                .andExpect(jsonPath("$.otpExpiraEnSegundos").value(300))
                .andExpect(jsonPath("$.intentosOtpRestantes").value(3));

        // Verificar que SÍ se envió el correo con el código OTP
        verify(emailService).enviarOtp(EMAIL, "789123");
    }

    @Test
    @DisplayName("Prueba 3 (SR-RF-92 Flujo Básico): Verificación de OTP exitosa emite JWT y notifica a ms-usuarios que el correo quedó verificado")
    void prueba3_SRRF92_VerificarOtpExitoso_EmiteJwtYAvisaMsUsuarios() throws Exception {
        when(otpService.obtenerUsuarioId(EMAIL)).thenReturn(25L);
        when(otpService.obtenerRol(EMAIL)).thenReturn("COMPRADOR");
        when(jwtService.generarToken(eq(25L), anySet())).thenReturn("jwt.token.verificado");

        VerificarOtpRequestDTO request = new VerificarOtpRequestDTO(EMAIL, "123456");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("jwt.token.verificado"))
                .andExpect(jsonPath("$.correoVerificado").value(true))
                .andExpect(jsonPath("$.verificacionPendiente").value(false));

        // Verificar eliminación de la sesión OTP en Redis y notificación a ms-usuarios
        verify(otpService).eliminarOtp(EMAIL);
        verify(usuarioClient).marcarCorreoVerificado(25L);
    }

    @Test
    @DisplayName("Prueba 4 (SR-RF-92 FA-08): Reenvío de OTP durante bloqueo de 60s responde HTTP 429 con cabecera Retry-After")
    void prueba4_SRRF92_ReenviarOtpBajoBloqueoTemporal_Retorna429ConRetryAfter() throws Exception {
        when(otpService.reenviarOtp(EMAIL))
                .thenThrow(new OtpBloqueadoTemporalmenteException(48L));

        ReenviarOtpRequestDTO request = new ReenviarOtpRequestDTO(EMAIL);

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "48"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("https://api.styleradar.com/errores/otp-reenvio-bloqueado"))
                .andExpect(jsonPath("$.title").value("Too Many Requests"))
                .andExpect(jsonPath("$.detail").value(containsString("48 segundos")))
                .andExpect(jsonPath("$.instance").value("/api/v1/auth/otp/resend"))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.codigo").value("OTP_REENVIO_BLOQUEADO"))
                .andExpect(jsonPath("$.esperarSegundos").value(48))
                .andExpect(jsonPath("$.message").value(containsString("48 segundos")));
    }

    @Test
    @DisplayName("Prueba 5 (SR-RF-92 Lógica de Dominio): OtpServiceImpl activa bloqueo de 60s tras 3 fallos e impide reenvío")
    void prueba5_SRRF92_OtpService_Bloqueo60SegundosTrasTresIntentosFallidos() {
        // Redis caído + respaldo en memoria explícito (solo dev) para validar la lógica del temporizador
        when(redisTemplate.opsForValue()).thenThrow(
                new org.springframework.data.redis.RedisConnectionFailureException("Redis dev offline"));
        when(redisTemplate.delete(anyString())).thenThrow(
                new org.springframework.data.redis.RedisConnectionFailureException("Redis dev offline"));
        OtpServiceImpl service = new OtpServiceImpl(redisTemplate, true, null);

        // Generar OTP para la sesión
        String codigoReal = service.generarOtp(EMAIL, 33L, "COMPRADOR");

        // Realizar 2 intentos fallidos con código incorrecto
        try {
            service.verificarOtp(EMAIL, "000000");
        } catch (Exception ignored) {
        }
        try {
            service.verificarOtp(EMAIL, "000000");
        } catch (Exception ignored) {
        }

        // El tercer intento fallido agota los 3 intentos y dispara OtpIntentosAgotadosException
        org.junit.jupiter.api.Assertions.assertThrows(OtpIntentosAgotadosException.class, () ->
                service.verificarOtp(EMAIL, "000000")
        );

        // Intentar reenviar OTP inmediatamente debe disparar OtpBloqueadoTemporalmenteException (bloqueo 60s activo)
        OtpBloqueadoTemporalmenteException bloqueado = org.junit.jupiter.api.Assertions.assertThrows(
                OtpBloqueadoTemporalmenteException.class, () -> service.reenviarOtp(EMAIL)
        );

        org.junit.jupiter.api.Assertions.assertTrue(bloqueado.getSegundosRestantes() > 0);
        org.junit.jupiter.api.Assertions.assertTrue(bloqueado.getMessage().contains("Debe esperar"));

        // Bug corregido: volver a iniciar sesión tampoco puede saltarse la espera (SR-RF-02 FA-15)
        org.junit.jupiter.api.Assertions.assertThrows(
                OtpBloqueadoTemporalmenteException.class, () -> service.generarOtp(EMAIL, 33L, "COMPRADOR"));

        // D1: se anula solo el código; la verificación en curso se conserva para pedir otro al terminar la espera
        org.junit.jupiter.api.Assertions.assertEquals(33L, service.obtenerUsuarioId(EMAIL));
        org.junit.jupiter.api.Assertions.assertEquals("COMPRADOR", service.obtenerRol(EMAIL));
        org.junit.jupiter.api.Assertions.assertEquals(0, service.obtenerIntentosRestantes(EMAIL));
    }
}
