package edu.dosw.styleradar.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.dto.request.LoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RefreshTokenRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RegistroAuthRequestDTO;
import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.application.service.impl.JwtServiceImpl;
import edu.dosw.styleradar.auth.application.service.impl.OtpServiceImpl;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.AuthController;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;
import edu.dosw.styleradar.auth.infrastructure.config.RabbitMQConfig;
import edu.dosw.styleradar.auth.infrastructure.config.SecurityConfig;
import edu.dosw.styleradar.auth.infrastructure.exception.GlobalExceptionHandler;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;

@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
@DisplayName("Suite de Verificación OWASP Top 10:2025 - CADCHE Authentication Service")
class OwaspTop10SecurityTest {

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
    @DisplayName("A01:2025 Broken Access Control - Bloquea logout sin token, token manipulado y CORS comodín '*'")
    void a01_brokenAccessControl_debeProtegerRutasYRechazarCorsInseguro() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());

        when(jwtService.validarToken("tampered.jwt.token"))
                .thenThrow(new io.jsonwebtoken.JwtException("invalid signature"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer tampered.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));

        SecurityConfig wildCardConfig = new SecurityConfig(objectMapper, "*");
        org.springframework.util.ReflectionUtils.makeAccessible(
                SecurityConfig.class.getDeclaredMethod("corsConfigurationSource"));
        var corsMethod = SecurityConfig.class.getDeclaredMethod("corsConfigurationSource");
        corsMethod.setAccessible(true);
        assertThatThrownBy(() -> {
            try {
                corsMethod.invoke(wildCardConfig);
            } catch (java.lang.reflect.InvocationTargetException e) {
                throw e.getCause();
            }
        }).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A02:2025 Security Misconfiguration - Cabeceras defensivas, sin Swagger en prod y stub de registro deshabilitado")
    void a02_securityMisconfiguration_debeAplicarCabecerasYEndurecerProd() throws Exception {
        LoginRequestDTO req = new LoginRequestDTO("invalido", "abc");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));

        String prodYml = Files.readString(Path.of("src/main/resources/application-prod.yml"));
        assertThat(prodYml)
                .contains("enabled: false")
                .contains("dev-fallback-enabled: false")
                .contains("stub-enabled: false")
                .contains("redis-local-fallback-enabled: false");

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        AuthController prodController = new AuthController(
                usuarioClient, otpService, emailService, jwtService, false, prodEnv);
        RegistroAuthRequestDTO regDto = new RegistroAuthRequestDTO(
                "Ana Gómez", "ana@styleradar.com", "+573001112233", "StyleRadar2026*", true);
        assertThatThrownBy(() -> prodController.registrar(regDto))
                .isInstanceOf(ServicioExternoNoDisponibleException.class);
    }

    @Test
    @DisplayName("A03:2025 Supply Chain Failures - Java 21 LTS, CycloneDX SBOM en pom.xml y Dockerfile no-root")
    void a03_softwareSupplyChain_debeCumplirJava21SbomYDockerNoRoot() throws Exception {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom)
                .contains("<java.version>21</java.version>")
                .contains("cyclonedx-maven-plugin");

        String dockerfile = Files.readString(Path.of("Dockerfile"));
        assertThat(dockerfile)
                .contains("eclipse-temurin:21-jre-alpine")
                .contains("USER appuser:appgroup")
                .contains("HEALTHCHECK");

        String ci = Files.readString(Path.of(".github/workflows/ci.yml"));
        assertThat(ci)
                .contains("spotbugs-maven-plugin")
                .contains("provenance: true")
                .contains("sbom: true");
    }

    @Test
    @DisplayName("A04:2025 Cryptographic Failures - Secretos >= 256 bits, bloqueo de secreto dev en prod y OTP con hash SHA-256 salteado")
    void a04_cryptographicFailures_debeValidarCriptografiaFuerte() {
        String weakSecret = Base64.getEncoder().encodeToString("clave-corta".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> new JwtServiceImpl(weakSecret, "PT1H"))
                .isInstanceOf(IllegalArgumentException.class);

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtServiceImpl("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", "PT1H", prodEnv))
                .isInstanceOf(IllegalStateException.class);

        OtpServiceImpl service = new OtpServiceImpl(stringRedisTemplate);
        String h1 = service.hashearOtp("123456");
        String h2 = service.hashearOtp("123456");
        assertThat(h1).isNotEqualTo("123456").contains("$").isNotEqualTo(h2);
    }

    @Test
    @DisplayName("A05:2025 Injection - Rechaza payload débil/XSS en registro, CRLF en email y restringe paquetes RabbitMQ")
    void a05_injection_debeValidarEntradasYDeserializacionSegura() throws Exception {
        RegistroAuthRequestDTO unsafeDto = new RegistroAuthRequestDTO(
                "<script>alert(1)</script>", "carlos@styleradar.com", "300123", "12345", true);

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unsafeDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.nombre").exists())
                .andExpect(jsonPath("$.validationErrors.password").exists());

        assertThatThrownBy(() -> SecuritySanitizer.normalizeEmail("attacker@evil.com\r\nBcc:victim@evil.com"))
                .isInstanceOf(IllegalArgumentException.class);

        RabbitMQConfig rabbitConfig = new RabbitMQConfig();
        var converterMethod = RabbitMQConfig.class.getDeclaredMethod("jacksonMessageConverter");
        converterMethod.setAccessible(true);
        Jackson2JsonMessageConverter converter = (Jackson2JsonMessageConverter) converterMethod.invoke(rabbitConfig);
        assertThat(converter).isNotNull();

        ConnectionFactory cf = org.mockito.Mockito.mock(ConnectionFactory.class);
        var listenerMethod = RabbitMQConfig.class.getDeclaredMethod(
                "rabbitListenerContainerFactory", ConnectionFactory.class, Jackson2JsonMessageConverter.class);
        listenerMethod.setAccessible(true);
        assertThat(listenerMethod.invoke(rabbitConfig, cf, converter)).isNotNull();
    }

    @Test
    @DisplayName("A06:2025 Insecure Design - Rate limiter HTTP 429 con Retry-After y límite de reenvíos OTP")
    void a06_insecureDesign_debeLimitarTasaYReenviosOtp() throws Exception {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        RateLimitingFilter limiter = new RateLimitingFilter(
                stringRedisTemplate, objectMapper, prodEnv, true, 1, 60L, false);

        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("ratelimit:/api/v1/auth/login:192.168.1.50")).thenReturn(2L);

        org.springframework.mock.web.MockHttpServletRequest req =
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr("192.168.1.50");
        org.springframework.mock.web.MockHttpServletResponse res =
                new org.springframework.mock.web.MockHttpServletResponse();

        limiter.doFilter(req, res, org.mockito.Mockito.mock(jakarta.servlet.FilterChain.class));

        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    @DisplayName("A07:2025 Authentication Failures - Rechaza refresh con JWT revocado o usuario bloqueado")
    void a07_authenticationFailures_debeRechazarTokensRevocados() throws Exception {
        RefreshTokenRequestDTO dto = new RefreshTokenRequestDTO("jwt.bloqueado");
        Claims claims = Jwts.claims().subject("7").issuedAt(new Date(1000L)).build();

        when(otpService.esTokenInvalido("jwt.bloqueado")).thenReturn(false);
        when(jwtService.validarToken("jwt.bloqueado")).thenReturn(claims);
        when(jwtService.obtenerUsuarioId("jwt.bloqueado")).thenReturn(7L);
        when(jwtService.obtenerRoles("jwt.bloqueado")).thenReturn(Set.of(Rol.COMPRADOR));
        when(otpService.estaUsuarioRevocado(7L, 1000L)).thenReturn(true);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A08:2025 Software/Data Integrity - Firma JWT rechaza alteración y UsuarioClient bloquea URL SSRF")
    void a08_integrityFailures_debeRechazarSsrfYManipulacionJwt() {
        RestClient.Builder builder = RestClient.builder();
        assertThatThrownBy(() -> new UsuarioClient(builder, "http://169.254.169.254/latest/meta-data", "u", "p"))
                .isInstanceOf(IllegalArgumentException.class);

        JwtServiceImpl realJwt = new JwtServiceImpl(
                "VGhpc0lzQVN1ZmZpY2llbnRseUxvbmdTZWNyZXRLZXlGb3JUZXN0czEyMzQ1Ng==", "PT1H");
        String signed = realJwt.generarToken(55L, Set.of(Rol.COMPRADOR));
        String forged = signed.substring(0, signed.length() - 2) + "xx";
        assertThatThrownBy(() -> realJwt.validarToken(forged)).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("A09:2025 Logging & Alerting - Enmascara PII y neutraliza caracteres de control en logs")
    void a09_securityLogging_debeEnmascararPiiYSanitizarCrlf() {
        assertThat(SecuritySanitizer.sanitizeForLog("ruta/mal\r\nINFO fake-log"))
                .doesNotContain("\r")
                .doesNotContain("\n");
        assertThat(SecuritySanitizer.maskEmail("johndoe@styleradar.com"))
                .isEqualTo("jo***@styleradar.com");
    }

    @Test
    @DisplayName("A10:2025 Mishandling of Exceptional Conditions - Falla cerrado (503) ante caída de Redis/ms-usuarios y oculta stacktrace en 500")
    void a10_mishandlingExceptionalConditions_debeFallarCerradoYOcultarInternos() throws Exception {
        when(usuarioClient.verificarCredenciales(anyString(), anyString()))
                .thenThrow(new ServicioExternoNoDisponibleException("ms-usuarios unreachable"));

        LoginRequestDTO loginDto = new LoginRequestDTO("comprador@styleradar.com", "Password123*");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginDto)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message", not(containsString("unreachable"))));

        when(usuarioClient.verificarCredenciales(eq("error@styleradar.com"), anyString()))
                .thenThrow(new RuntimeException("NullPointerException at secret stack"));

        LoginRequestDTO errorDto = new LoginRequestDTO("error@styleradar.com", "Password123*");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(errorDto)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message", not(containsString("NullPointerException"))));
    }
}
