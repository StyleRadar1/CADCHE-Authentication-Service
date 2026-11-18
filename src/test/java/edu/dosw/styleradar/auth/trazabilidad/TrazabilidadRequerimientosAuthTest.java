package edu.dosw.styleradar.auth.trazabilidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;

/**
 * Trazabilidad del análisis de requerimientos y del contrato Front ↔ ms-auth (v1.2) de ms-auth (StyleRadar_Requerimientos_ms-auth_CADche, 13/11/2026).
 * Cada prueba corresponde a un flujo alterno (FA) o regla de negocio (RN) de SR-RF-02 y SR-RF-92 y verifica, a través
 * de la API real con Redis 7, el código HTTP, el código de error y el mensaje exactos del documento.
 * Solo se simulan los sistemas externos: ms-usuarios (servidor HTTP local), Google (decodificador del ID token) y el
 * envío del correo (se captura el código).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.mail.username=test@styleradar.com",
        "styleradar.security.jwt.secret-base64=VGhpc0lzQVN1ZmZpY2llbnRseUxvbmdTZWNyZXRLZXlGb3JUZXN0czEyMzQ1Ng==",
        "styleradar.security.oauth2.google.client-id=cliente-styleradar.apps.googleusercontent.com",
        "styleradar.usuarios.username=ms-auth",
        "styleradar.usuarios.password=prueba",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "styleradar.security.jwt.duration=PT12H"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Trazabilidad del Word de ms-auth (SR-RF-02 y SR-RF-92)")
class TrazabilidadRequerimientosAuthTest {

    private static final String CLAVE = "StyleRadar2026*";
    private static final String INVALID_DATA = "La solicitud contiene datos inválidos";
    private static final String NO_DISPONIBLE =
            "El servicio requerido no está disponible temporalmente. Intente nuevamente más tarde.";
    private static final String DEMASIADAS = "Demasiadas solicitudes en un período corto. Intente nuevamente más tarde.";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static HttpServer msUsuarios;
    private static final Map<String, Map<String, Object>> CUENTAS = new ConcurrentHashMap<>();
    private static final List<Long> CORREOS_VERIFICADOS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger IP = new AtomicInteger(1);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StringRedisTemplate redis;

    @MockBean
    private EmailService emailService;
    @MockBean
    private JavaMailSender javaMailSender;
    @MockBean
    private JwtDecoder decodificadorGoogle;

    private final Map<String, String> ultimoCodigo = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) throws IOException {
        iniciarMsUsuarios();
        registro.add("spring.data.redis.host", REDIS::getHost);
        registro.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registro.add("styleradar.usuarios.base-url", () -> "http://127.0.0.1:" + msUsuarios.getAddress().getPort());
    }

    @AfterAll
    static void detener() {
        if (msUsuarios != null) {
            msUsuarios.stop(0);
        }
    }

    @BeforeEach
    void preparar() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        CORREOS_VERIFICADOS.clear();
        CUENTAS.clear();
        cuenta("verificado@styleradar.com", 12, "BUYER", "ACTIVE", true);
        cuenta("nuevo@styleradar.com", 13, "STORE_ADMIN", "ACTIVE", false);
        cuenta("bloqueado@styleradar.com", 14, "BUYER", "BLOCKED", true);
        cuenta("inactivo@styleradar.com", 15, "BUYER", "INACTIVE", true);
        cuenta("caido@styleradar.com", 16, "BUYER", "ACTIVE", true);
        cuenta("google@styleradar.com", 17, "FOUNDATION_REPRESENTATIVE", "ACTIVE", false);
        cuenta("googlebloqueado@styleradar.com", 18, "BUYER", "BLOCKED", true);
        reset(emailService);
        ultimoCodigo.clear();
        doAnswer(inv -> {
            ultimoCodigo.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(emailService).enviarOtp(anyString(), anyString());
    }

    // ---------------------------------------------------------------- utilidades

    private static void cuenta(String email, long id, String rol, String estado, boolean verificado) {
        CUENTAS.put(email, new ConcurrentHashMap<>(Map.of(
                "id", id, "email", email, "role", rol, "status", estado, "emailVerified", verificado)));
    }

    private static void iniciarMsUsuarios() throws IOException {
        if (msUsuarios != null) {
            return;
        }
        msUsuarios = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ObjectMapper json = new ObjectMapper();
        msUsuarios.createContext("/internal/api/v1/users/verify-credentials", seguro(ex -> {
            JsonNode cuerpo = json.readTree(ex.getRequestBody());
            Map<String, Object> c = CUENTAS.get(cuerpo.path("email").asText());
            if (c != null && "caido@styleradar.com".equals(c.get("email"))) {
                responder(ex, 500, "{}");
            } else if (c == null || !CLAVE.equals(cuerpo.path("password").asText())) {
                responder(ex, 401, "{}");
            } else if (!"ACTIVE".equals(c.get("status"))) {
                responder(ex, 403, "{}");
            } else {
                responder(ex, 200, json.writeValueAsString(c));
            }
        }));
        msUsuarios.createContext("/internal/api/v1/users/oauth2", seguro(ex -> {
            JsonNode cuerpo = json.readTree(ex.getRequestBody());
            Map<String, Object> c = CUENTAS.get(cuerpo.path("email").asText());
            responder(ex, c == null ? 404 : 200, c == null ? "{}" : json.writeValueAsString(c));
        }));
        msUsuarios.createContext("/internal/api/v1/users/", seguro(ex -> {
            String[] partes = ex.getRequestURI().getPath().split("/");
            if ("PATCH".equals(ex.getRequestMethod()) && ex.getRequestURI().getPath().endsWith("/verify-email")) {
                long id = Long.parseLong(partes[partes.length - 2]);
                CORREOS_VERIFICADOS.add(id);
                CUENTAS.values().stream().filter(c -> ((Number) c.get("id")).longValue() == id)
                        .forEach(c -> c.put("emailVerified", true));
                responder(ex, 204, null);
            } else {
                responder(ex, 404, "{}");
            }
        }));
        msUsuarios.start();
    }

    /** Si el simulador falla, responde 500 con el motivo en vez de cortar la conexión. */
    private static com.sun.net.httpserver.HttpHandler seguro(com.sun.net.httpserver.HttpHandler manejador) {
        return ex -> {
            try {
                manejador.handle(ex);
            } catch (RuntimeException | IOException e) {
                e.printStackTrace();
                responder(ex, 500, "{\"error\":\"" + e.getClass().getSimpleName() + "\"}");
            }
        };
    }

    private static void responder(HttpExchange ex, int estado, String cuerpo) throws IOException {
        try {
            ex.getRequestBody().readAllBytes();
        } catch (IOException cerrado) {
            // El cuerpo ya se leyó y cerró al convertirlo a JSON.
        }
        if (cuerpo == null) {
            ex.sendResponseHeaders(estado, -1);
        } else {
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(estado, bytes.length);
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }

    /** Cada prueba usa su propia IP para que el límite de 15 solicitudes por minuto no se mezcle entre pruebas. */
    private String nuevaIp() {
        return "10.0.0." + IP.incrementAndGet();
    }

    private ResultActions llamar(String ip, String ruta, Object cuerpo) throws Exception {
        return mockMvc.perform(post("/api/v1/auth" + ruta)
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions login(String ip, String email, String clave) throws Exception {
        return llamar(ip, "/login", Map.of("email", email, "password", clave));
    }

    private ResultActions verificar(String ip, String email, String codigo) throws Exception {
        return llamar(ip, "/otp/verify", Map.of("email", email, "otpCode", codigo));
    }

    private ResultActions reenviar(String ip, String email) throws Exception {
        return llamar(ip, "/otp/resend", Map.of("email", email));
    }

    /** Formato único de error del Word: código HTTP, código de error, mensaje y fecha AAAA-MM-DDThh:mm:ss. */
    private static ResultActions error(ResultActions r, int http, String codigo, String mensaje) throws Exception {
        r.andExpect(status().is(http))
                .andExpect(jsonPath("$.code").value(codigo))
                .andExpect(jsonPath("$.status").value(http))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
        if (mensaje != null) {
            r.andExpect(jsonPath("$.message").value(mensaje));
        }
        return r;
    }

    private static Jwt tokenGoogle(String email, boolean verificado) {
        return Jwt.withTokenValue("id-token").header("alg", "RS256")
                .subject("google-" + email).claim("email", email).claim("email_verified", verificado)
                .claim("name", "Usuario Google").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .build();
    }

    // ---------------------------------------------------------------- SR-RF-02

    @Nested
    @DisplayName("SR-RF-02 Iniciar sesión")
    class IniciarSesion {

        @Test
        @DisplayName("Flujo básico y RN-05: correo verificado entra sin código con todos los datos de salida")
        void flujoBasico() throws Exception {
            login(nuevaIp(), "verificado@styleradar.com", CLAVE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(43200))
                    .andExpect(jsonPath("$.userId").value(12))
                    .andExpect(jsonPath("$.roles[0]").value("BUYER"))
                    .andExpect(jsonPath("$.email").value("verificado@styleradar.com"))
                    .andExpect(jsonPath("$.emailVerified").value(true))
                    .andExpect(jsonPath("$.otpRequired").value(false));
            assertThat(ultimoCodigo).isEmpty();
        }

        @Test
        @DisplayName("Correo sin distinguir mayúsculas y sin espacios alrededor")
        void correoNormalizado() throws Exception {
            login(nuevaIp(), "  Verificado@StyleRadar.COM ", CLAVE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("verificado@styleradar.com"));
        }

        @Test
        @DisplayName("FA-01: correo vacío → 400 «El correo electrónico es obligatorio»")
        void fa01() throws Exception {
            ResultActions r = login(nuevaIp(), "", CLAVE);
            error(r, 400, "INVALID_DATA", INVALID_DATA);
            r.andExpect(jsonPath("$.errors.email").value("El correo electrónico es obligatorio"));
        }

        @Test
        @DisplayName("FA-02: correo con formato inválido → 400")
        void fa02() throws Exception {
            ResultActions r = login(nuevaIp(), "correo-sin-arroba", CLAVE);
            error(r, 400, "INVALID_DATA", INVALID_DATA);
            r.andExpect(jsonPath("$.errors.email").value("El formato del correo electrónico es inválido"));
        }

        @Test
        @DisplayName("FA-03: correo de más de 254 caracteres → 400")
        void fa03() throws Exception {
            ResultActions r = login(nuevaIp(), "a".repeat(250) + "@x.co", CLAVE);
            error(r, 400, "INVALID_DATA", INVALID_DATA);
            r.andExpect(jsonPath("$.errors.email")
                    .value("El correo electrónico excede la longitud máxima permitida"));
        }

        @Test
        @DisplayName("FA-04: contraseña vacía → 400 «La contraseña es obligatoria»")
        void fa04() throws Exception {
            ResultActions r = login(nuevaIp(), "verificado@styleradar.com", "");
            error(r, 400, "INVALID_DATA", INVALID_DATA);
            r.andExpect(jsonPath("$.errors.password").value("La contraseña es obligatoria"));
        }

        @Test
        @DisplayName("FA-05: contraseña de menos de 8 o más de 72 caracteres → 400")
        void fa05() throws Exception {
            for (String clave : new String[] {"1234567", "x".repeat(73)}) {
                ResultActions r = login(nuevaIp(), "verificado@styleradar.com", clave);
                error(r, 400, "INVALID_DATA", INVALID_DATA);
                r.andExpect(jsonPath("$.errors.password")
                        .value("La contraseña debe tener entre 8 y 30 caracteres"));
            }
        }

        @Test
        @DisplayName("FA-06 y RN-04: credenciales incorrectas → 401 con el mismo mensaje si falla el correo o la clave")
        void fa06() throws Exception {
            error(login(nuevaIp(), "verificado@styleradar.com", "Incorrecta1*"),
                    401, "INVALID_CREDENTIALS", "El correo o la contraseña son incorrectos");
            error(login(nuevaIp(), "noexiste@styleradar.com", CLAVE),
                    401, "INVALID_CREDENTIALS", "El correo o la contraseña son incorrectos");
        }

        @Test
        @DisplayName("FA-07 y RN-03: cuenta Inactiva o Bloqueada → 403")
        void fa07() throws Exception {
            for (String email : new String[] {"bloqueado@styleradar.com", "inactivo@styleradar.com"}) {
                error(login(nuevaIp(), email, CLAVE),
                        403, "ACCOUNT_NOT_ACTIVE", "La cuenta se encuentra inactiva o bloqueada");
            }
        }

        @Test
        @DisplayName("FA-08: más de 15 intentos en un minuto desde el mismo equipo → 429 con Retry-After")
        void fa08() throws Exception {
            String ip = nuevaIp();
            for (int i = 0; i < 15; i++) {
                login(ip, "noexiste@styleradar.com", CLAVE).andExpect(status().isUnauthorized());
            }
            ResultActions r = login(ip, "noexiste@styleradar.com", CLAVE);
            error(r, 429, "RATE_LIMIT_EXCEEDED", DEMASIADAS);
            r.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        }

        @Test
        @DisplayName("FA-09: ms-usuarios o el correo no disponibles → 503")
        void fa09() throws Exception {
            error(login(nuevaIp(), "caido@styleradar.com", CLAVE), 503, "SERVICE_UNAVAILABLE", NO_DISPONIBLE);
            doThrow(new ServicioExternoNoDisponibleException("SMTP caído"))
                    .when(emailService).enviarOtp(anyString(), anyString());
            error(login(nuevaIp(), "nuevo@styleradar.com", CLAVE), 503, "SERVICE_UNAVAILABLE", NO_DISPONIBLE);
        }

        @Test
        @DisplayName("FA-10 y RN-06: Google válido entra sin código y el correo queda verificado")
        void fa10() throws Exception {
            when(decodificadorGoogle.decode(any())).thenReturn(tokenGoogle("google@styleradar.com", true));
            llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "id-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.otpRequired").value(false))
                    .andExpect(jsonPath("$.roles[0]").value("FOUNDATION_REPRESENTATIVE"));
            assertThat(CORREOS_VERIFICADOS).contains(17L);
            assertThat(ultimoCodigo).isEmpty();
        }

        @Test
        @DisplayName("FA-11: ID token de Google inválido o con correo sin verificar → 401")
        void fa11() throws Exception {
            when(decodificadorGoogle.decode(any())).thenThrow(new BadJwtException("firma inválida"));
            error(llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "malo")), 401, "INVALID_CREDENTIALS",
                    "El ID Token de Google es inválido, expiró o no pertenece a StyleRadar");
            reset(decodificadorGoogle);
            when(decodificadorGoogle.decode(any())).thenReturn(tokenGoogle("google@styleradar.com", false));
            error(llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "sin-verificar")), 401,
                    "INVALID_CREDENTIALS", "La cuenta de Google no tiene el correo electrónico verificado");
        }

        @Test
        @DisplayName("FA-12: con Google, cuenta de StyleRadar bloqueada → 403 «Tu cuenta está {estado}…»")
        void fa12() throws Exception {
            when(decodificadorGoogle.decode(any())).thenReturn(tokenGoogle("googlebloqueado@styleradar.com", true));
            error(llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "id-token")), 403, "ACCOUNT_NOT_ACTIVE",
                    "Tu cuenta está BLOQUEADA. Contacta al soporte de StyleRadar.");
        }

        @Test
        @DisplayName("FA-13: correo sin verificar → 200 sin token, verificación pendiente, 300 s y 3 intentos")
        void fa13() throws Exception {
            login(nuevaIp(), "nuevo@styleradar.com", CLAVE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(nullValue()))
                    .andExpect(jsonPath("$.emailVerified").value(false))
                    .andExpect(jsonPath("$.otpRequired").value(true))
                    .andExpect(jsonPath("$.otpExpiresInSeconds").value(300))
                    .andExpect(jsonPath("$.otpAttemptsRemaining").value(3));
            assertThat(ultimoCodigo.get("nuevo@styleradar.com")).matches("\\d{6}");
        }

        @Test
        @DisplayName("FA-14: con Google, Google o ms-usuarios no disponibles → 503")
        void fa14() throws Exception {
            when(decodificadorGoogle.decode(any())).thenReturn(tokenGoogle("noregistrado@styleradar.com", true));
            error(llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "id-token")),
                    503, "SERVICE_UNAVAILABLE", NO_DISPONIBLE);
        }

        @Test
        @DisplayName("FA-15: correo sin verificar durante la espera de 60 s → 429 sin enviar código")
        void fa15() throws Exception {
            String ip = nuevaIp();
            login(ip, "nuevo@styleradar.com", CLAVE).andExpect(status().isOk());
            for (int i = 0; i < 3; i++) {
                verificar(ip, "nuevo@styleradar.com", "000000");
            }
            ultimoCodigo.clear();
            ResultActions r = login(ip, "nuevo@styleradar.com", CLAVE);
            error(r, 429, "OTP_RESEND_BLOCKED", null);
            r.andExpect(jsonPath("$.message").value(matchesPattern("Debe esperar \\d+ segundos para solicitar un nuevo código\\.")))
                    .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
            assertThat(ultimoCodigo).isEmpty();
        }
    }

    // ---------------------------------------------------------------- SR-RF-92

    @Nested
    @DisplayName("SR-RF-92 Verificar el correo con código OTP")
    class VerificarCorreo {

        private String ip;

        @BeforeEach
        void pedirCodigo() throws Exception {
            ip = nuevaIp();
            login(ip, "nuevo@styleradar.com", CLAVE).andExpect(status().isOk());
        }

        private String codigoIncorrecto() {
            return "000000".equals(ultimoCodigo.get("nuevo@styleradar.com")) ? "111111" : "000000";
        }

        @Test
        @DisplayName("Flujo básico, RN-03 y RN-07: código correcto → token, correo verificado y siguiente login sin código")
        void flujoBasico() throws Exception {
            String codigo = ultimoCodigo.get("nuevo@styleradar.com");
            verificar(ip, "nuevo@styleradar.com", codigo)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.emailVerified").value(true))
                    .andExpect(jsonPath("$.roles[0]").value("STORE_ADMIN"))
                    .andExpect(jsonPath("$.email").value("nuevo@styleradar.com"));
            assertThat(CORREOS_VERIFICADOS).contains(13L);
            error(verificar(ip, "nuevo@styleradar.com", codigo), 401, "OTP_EXPIRED",
                    "El código OTP expiró o no existe. Debe solicitar uno nuevo.");
            login(ip, "nuevo@styleradar.com", CLAVE).andExpect(jsonPath("$.otpRequired").value(false));
        }

        @Test
        @DisplayName("RN-08: el código no se guarda en texto plano en Redis")
        void rn08() {
            String codigo = ultimoCodigo.get("nuevo@styleradar.com");
            String guardado = redis.opsForValue().get("otp:nuevo@styleradar.com");
            assertThat(guardado).isNotBlank().isNotEqualTo(codigo).doesNotContain(codigo);
        }

        @Test
        @DisplayName("FA-01: código vacío → 400 «El código OTP es obligatorio»")
        void fa01() throws Exception {
            ResultActions r = verificar(ip, "nuevo@styleradar.com", "");
            error(r, 400, "INVALID_DATA", INVALID_DATA);
            r.andExpect(jsonPath("$.errors.otpCode").value("El código OTP es obligatorio"));
        }

        @Test
        @DisplayName("FA-02: código sin exactamente 6 dígitos → 400")
        void fa02() throws Exception {
            for (String codigo : new String[] {"12345", "1234567", "12a456"}) {
                ResultActions r = verificar(ip, "nuevo@styleradar.com", codigo);
                error(r, 400, "INVALID_DATA", INVALID_DATA);
                r.andExpect(jsonPath("$.errors.otpCode")
                        .value("El código OTP debe contener exactamente 6 dígitos numéricos"));
            }
        }

        @Test
        @DisplayName("FA-03: correo vacío o inválido al verificar → 400")
        void fa03() throws Exception {
            verificar(ip, "", "123456")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").value("El correo electrónico es obligatorio"));
            verificar(ip, "sin-arroba", "123456")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.email").value("El formato del correo electrónico es inválido"));
        }

        @Test
        @DisplayName("FA-04: código incorrecto con intentos → 401 con intentosRestantes 2 y luego 1")
        void fa04() throws Exception {
            ResultActions r = verificar(ip, "nuevo@styleradar.com", codigoIncorrecto());
            error(r, 401, "OTP_INCORRECT", "El código OTP es incorrecto. Intentos restantes: 2");
            r.andExpect(jsonPath("$.attemptsRemaining").value(2));
            verificar(ip, "nuevo@styleradar.com", codigoIncorrecto())
                    .andExpect(jsonPath("$.attemptsRemaining").value(1));
        }

        @Test
        @DisplayName("FA-05 y RN-04: tercer código incorrecto → 429, 60 s de espera y el código se anula")
        void fa05() throws Exception {
            String correcto = ultimoCodigo.get("nuevo@styleradar.com");
            verificar(ip, "nuevo@styleradar.com", codigoIncorrecto());
            verificar(ip, "nuevo@styleradar.com", codigoIncorrecto());
            ResultActions r = verificar(ip, "nuevo@styleradar.com", codigoIncorrecto());
            error(r, 429, "OTP_ATTEMPTS_EXHAUSTED",
                    "Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.");
            r.andExpect(jsonPath("$.retryAfterSeconds").value(60))
                    .andExpect(jsonPath("$.attemptsRemaining").value(0))
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
            error(verificar(ip, "nuevo@styleradar.com", correcto), 401, "OTP_EXPIRED", null);
        }

        @Test
        @DisplayName("FA-06: código vencido o anulado → 401 «El código OTP expiró o no existe…»")
        void fa06() throws Exception {
            redis.delete("otp:nuevo@styleradar.com");
            error(verificar(ip, "nuevo@styleradar.com", "123456"), 401, "OTP_EXPIRED",
                    "El código OTP expiró o no existe. Debe solicitar uno nuevo.");
        }

        @Test
        @DisplayName("FA-07 y RN-03: pedir código nuevo anula el anterior; respuesta con 300 s, 3 intentos y reenvíos restantes")
        void fa07() throws Exception {
            String anterior = ultimoCodigo.get("nuevo@styleradar.com");
            reenviar(ip, "nuevo@styleradar.com")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").value("Se envió un código de verificación de 6 dígitos a su correo registrado"))
                    .andExpect(jsonPath("$.email").value("nuevo@styleradar.com"))
                    .andExpect(jsonPath("$.expiresInSeconds").value(300))
                    .andExpect(jsonPath("$.attemptsRemaining").value(3))
                    .andExpect(jsonPath("$.resendsRemaining").value(2));
            String nuevo = ultimoCodigo.get("nuevo@styleradar.com");
            if (!nuevo.equals(anterior)) {
                error(verificar(ip, "nuevo@styleradar.com", anterior), 401, "OTP_INCORRECT", null);
            }
            verificar(ip, "nuevo@styleradar.com", nuevo).andExpect(status().isOk());
        }

        @Test
        @DisplayName("FA-08: pedir código nuevo durante la espera de 60 s → 429; pasada la espera se puede pedir")
        void fa08() throws Exception {
            for (int i = 0; i < 3; i++) {
                verificar(ip, "nuevo@styleradar.com", codigoIncorrecto());
            }
            ResultActions r = reenviar(ip, "nuevo@styleradar.com");
            error(r, 429, "OTP_RESEND_BLOCKED", null);
            r.andExpect(jsonPath("$.message").value(matchesPattern("Debe esperar \\d+ segundos para solicitar un nuevo código\\.")))
                    .andExpect(jsonPath("$.retryAfterSeconds").isNumber());
            redis.delete("otp:bloqueo:nuevo@styleradar.com");
            reenviar(ip, "nuevo@styleradar.com").andExpect(status().isOk());
        }

        @Test
        @DisplayName("FA-09: código nuevo sin verificación en curso → 401")
        void fa09() throws Exception {
            error(reenviar(ip, "sinsesion@styleradar.com"), 401, "OTP_SESSION_NOT_FOUND",
                    "No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.");
        }

        @Test
        @DisplayName("FA-10 y RN-05: más de 3 códigos nuevos en la misma verificación → 429")
        void fa10() throws Exception {
            for (int i = 0; i < 3; i++) {
                reenviar(ip, "nuevo@styleradar.com").andExpect(status().isOk());
            }
            error(reenviar(ip, "nuevo@styleradar.com"), 429, "RESENDS_EXHAUSTED",
                    "Se superó el máximo de reenvíos permitidos para esta sesión. Inicie sesión nuevamente.");
        }

        @Test
        @DisplayName("FA-11: más de 15 solicitudes de verificación en un minuto → 429")
        void fa11() throws Exception {
            for (int i = 0; i < 15; i++) {
                verificar(ip, "nuevo@styleradar.com", "12a456");
            }
            error(verificar(ip, "nuevo@styleradar.com", "12a456"), 429, "RATE_LIMIT_EXCEEDED", DEMASIADAS);
        }

        @Test
        @DisplayName("FA-12: el correo no está disponible al pedir código nuevo → 503 y el correo no queda verificado")
        void fa12() throws Exception {
            doThrow(new ServicioExternoNoDisponibleException("SMTP caído"))
                    .when(emailService).enviarOtp(eq("nuevo@styleradar.com"), anyString());
            error(reenviar(ip, "nuevo@styleradar.com"), 503, "SERVICE_UNAVAILABLE", NO_DISPONIBLE);
            assertThat(CORREOS_VERIFICADOS).doesNotContain(13L);
        }

        @Test
        @DisplayName("FA-13: cierra sin verificar → el siguiente login vuelve a enviar un código nuevo")
        void fa13() throws Exception {
            String primero = ultimoCodigo.remove("nuevo@styleradar.com");
            login(ip, "nuevo@styleradar.com", CLAVE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.otpRequired").value(true));
            assertThat(ultimoCodigo.get("nuevo@styleradar.com")).isNotNull().matches("\\d{6}");
            assertThat(primero).matches("\\d{6}");
        }

        @Test
        @DisplayName("RN-06: la verificación en curso dura 15 minutos")
        void rn06() {
            Long ttl = redis.getExpire("otp:usuario:nuevo@styleradar.com");
            assertThat(ttl).isBetween(890L, 900L);
        }

        @Test
        @DisplayName("RN-02: el código vence a los 5 minutos")
        void rn02() {
            Long ttl = redis.getExpire("otp:nuevo@styleradar.com");
            assertThat(ttl).isBetween(290L, 300L);
        }
    }

    // ---------------------------------------------------------------- Contrato Front ↔ ms-auth v1.2

    @Nested
    @DisplayName("Contrato Front ↔ ms-auth v1.2")
    class ContratoFront {

        private JsonNode cuerpo(ResultActions r) throws Exception {
            return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        }

        private String tokenDe(ResultActions r) throws Exception {
            return cuerpo(r).get("accessToken").asText();
        }

        private ResultActions logout(String ip, String token) throws Exception {
            return mockMvc.perform(post("/api/v1/auth/logout").with(req -> {
                req.setRemoteAddr(ip);
                return req;
            }).header(HttpHeaders.AUTHORIZATION, token == null ? "" : "Bearer " + token));
        }

        @Test
        @DisplayName("1.4: el error trae timestamp, status, error, codigo, message, path, validationErrors, intentosRestantes y esperarSegundos")
        void formatoUnicoDeError() throws Exception {
            String ip = nuevaIp();
            login(ip, "nuevo@styleradar.com", CLAVE).andExpect(status().isOk());
            String codigo = ultimoCodigo.get("nuevo@styleradar.com");
            String malo = "000000".equals(codigo) ? "111111" : "000000";
            verificar(ip, "nuevo@styleradar.com", malo);
            verificar(ip, "nuevo@styleradar.com", malo);
            ResultActions r = verificar(ip, "nuevo@styleradar.com", malo);
            JsonNode e = cuerpo(r);
            assertThat(e.fieldNames()).toIterable().contains("timestamp", "status", "error", "code", "message", "path",
                    "errors", "attemptsRemaining", "retryAfterSeconds");
            assertThat(e.get("status").asInt()).isEqualTo(429);
            assertThat(e.get("error").asText()).isEqualTo("Too Many Requests");
            assertThat(e.get("code").asText()).isEqualTo("OTP_ATTEMPTS_EXHAUSTED");
            assertThat(e.get("message").asText())
                    .isEqualTo("Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.");
            assertThat(e.get("path").asText()).isEqualTo("/api/v1/auth/otp/verify");
            assertThat(e.get("errors").isObject()).isTrue();
            assertThat(e.get("errors").size()).isZero();
            assertThat(e.get("attemptsRemaining").asInt()).isZero();
            assertThat(e.get("retryAfterSeconds").asInt()).isEqualTo(60);
            r.andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));

            JsonNode sinExtras = cuerpo(login(nuevaIp(), "noexiste@styleradar.com", CLAVE));
            assertThat(sinExtras.fieldNames()).toIterable().contains("attemptsRemaining", "retryAfterSeconds", "errors");
            assertThat(sinExtras.get("attemptsRemaining").isNull()).isTrue();
            assertThat(sinExtras.get("retryAfterSeconds").isNull()).isTrue();
        }

        @Test
        @DisplayName("1.2 y 1.4: todos los 429 llevan Retry-After igual a esperarSegundos")
        void retryAfterEnTodos429() throws Exception {
            String ip = nuevaIp();
            for (int i = 0; i < 15; i++) {
                login(ip, "noexiste@styleradar.com", CLAVE);
            }
            ResultActions limite = login(ip, "noexiste@styleradar.com", CLAVE);
            String espera = limite.andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER);
            assertThat(espera).isNotBlank();
            assertThat(cuerpo(limite).get("retryAfterSeconds").asText()).isEqualTo(espera);

            String ip2 = nuevaIp();
            login(ip2, "nuevo@styleradar.com", CLAVE);
            for (int i = 0; i < 3; i++) {
                verificar(ip2, "nuevo@styleradar.com", "000000".equals(ultimoCodigo.get("nuevo@styleradar.com")) ? "111111" : "000000");
            }
            ResultActions bloqueo = reenviar(ip2, "nuevo@styleradar.com");
            error(bloqueo, 429, "OTP_RESEND_BLOCKED", null);
            assertThat(bloqueo.andReturn().getResponse().getHeader(HttpHeaders.RETRY_AFTER))
                    .isEqualTo(cuerpo(bloqueo).get("retryAfterSeconds").asText());

            String ip3 = nuevaIp();
            login(ip3, "nuevo@styleradar.com", CLAVE);
            redis.delete("otp:bloqueo:nuevo@styleradar.com");
            for (int i = 0; i < 3; i++) {
                reenviar(ip3, "nuevo@styleradar.com").andExpect(status().isOk());
            }
            ResultActions agotados = reenviar(ip3, "nuevo@styleradar.com");
            error(agotados, 429, "RESENDS_EXHAUSTED", null);
            agotados.andExpect(header().string(HttpHeaders.RETRY_AFTER, "0")).andExpect(jsonPath("$.retryAfterSeconds").value(0));
        }

        @Test
        @DisplayName("1.5: OTP_INCORRECT trae el mensaje con los intentos e intentosRestantes como número")
        void otpIncorrecto() throws Exception {
            String ip = nuevaIp();
            login(ip, "nuevo@styleradar.com", CLAVE);
            String malo = "000000".equals(ultimoCodigo.get("nuevo@styleradar.com")) ? "111111" : "000000";
            error(verificar(ip, "nuevo@styleradar.com", malo), 401, "OTP_INCORRECT",
                    "El código OTP es incorrecto. Intentos restantes: 2")
                    .andExpect(jsonPath("$.attemptsRemaining").value(2));
            error(verificar(ip, "nuevo@styleradar.com", malo), 401, "OTP_INCORRECT",
                    "El código OTP es incorrecto. Intentos restantes: 1")
                    .andExpect(jsonPath("$.attemptsRemaining").value(1));
        }

        @Test
        @DisplayName("1.5: INVALID_CREDENTIALS y ACCOUNT_NOT_ACTIVE con el mensaje del catálogo")
        void credencialesYCuenta() throws Exception {
            error(login(nuevaIp(), "verificado@styleradar.com", "OtraClave2026*"), 401, "INVALID_CREDENTIALS",
                    "El correo o la contraseña son incorrectos");
            error(login(nuevaIp(), "inactivo@styleradar.com", CLAVE), 403, "ACCOUNT_NOT_ACTIVE",
                    "La cuenta se encuentra inactiva o bloqueada");
        }

        @Test
        @DisplayName("1.5: error no controlado → 500 INTERNAL_ERROR sin detalles internos")
        void errorInterno() throws Exception {
            doThrow(new IllegalStateException("detalle interno"))
                    .when(emailService).enviarOtp(anyString(), anyString());
            ResultActions r = login(nuevaIp(), "nuevo@styleradar.com", CLAVE);
            error(r, 500, "INTERNAL_ERROR", "Ocurrió un error interno. Intente nuevamente más tarde.");
            assertThat(r.andReturn().getResponse().getContentAsString()).doesNotContain("detalle interno");
        }

        @Test
        @DisplayName("1.6: idToken vacío → 400 «El ID Token de Google es obligatorio»")
        void idTokenObligatorio() throws Exception {
            ResultActions r = llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", ""));
            error(r, 400, "INVALID_DATA", INVALID_DATA);
            r.andExpect(jsonPath("$.errors.idToken").value("El ID Token de Google es obligatorio"));
        }

        @Test
        @DisplayName("3.3: correo verificado → todos los campos, expiresIn desde JWT_DURATION y JWT con sub y roles")
        void salidaLoginVerificado() throws Exception {
            ResultActions r = login(nuevaIp(), "verificado@styleradar.com", CLAVE).andExpect(status().isOk());
            JsonNode b = cuerpo(r);
            assertThat(b.fieldNames()).toIterable().contains("accessToken", "tokenType", "expiresIn", "userId", "roles",
                    "email", "emailVerified", "otpRequired", "otpExpiresInSeconds", "otpAttemptsRemaining");
            assertThat(b.get("expiresIn").asLong()).isEqualTo(43200);
            assertThat(b.get("roles").size()).isEqualTo(1);
            assertThat(b.get("otpExpiresInSeconds").isNull()).isTrue();
            assertThat(b.get("otpAttemptsRemaining").asInt()).isZero();
            String[] partes = b.get("accessToken").asText().split("\\.");
            JsonNode claims = objectMapper.readTree(java.util.Base64.getUrlDecoder().decode(partes[1]));
            assertThat(claims.get("sub").asText()).isEqualTo("12");
            assertThat(claims.get("roles").get(0).asText()).isEqualTo("BUYER");
            assertThat(claims.get("exp").asLong() - claims.get("iat").asLong()).isEqualTo(43200);
        }

        @Test
        @DisplayName("3.3: correo sin verificar → accessToken null, tokenType Bearer y expiresIn desde la configuración")
        void salidaLoginSinVerificar() throws Exception {
            login(nuevaIp(), "nuevo@styleradar.com", CLAVE)
                    .andExpect(jsonPath("$.accessToken").value(nullValue()))
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(43200))
                    .andExpect(jsonPath("$.userId").value(13))
                    .andExpect(jsonPath("$.roles[0]").value("STORE_ADMIN"))
                    .andExpect(jsonPath("$.email").value("nuevo@styleradar.com"));
        }

        @Test
        @DisplayName("4.1: verificar → sesión completa con requiereOtp false, otpExpiraEnSegundos null e intentos 0")
        void salidaVerificar() throws Exception {
            String ip = nuevaIp();
            login(ip, "nuevo@styleradar.com", CLAVE);
            verificar(ip, "nuevo@styleradar.com", ultimoCodigo.get("nuevo@styleradar.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(43200))
                    .andExpect(jsonPath("$.userId").value(13))
                    .andExpect(jsonPath("$.otpRequired").value(false))
                    .andExpect(jsonPath("$.otpExpiresInSeconds").value(nullValue()))
                    .andExpect(jsonPath("$.otpAttemptsRemaining").value(0));
        }

        @Test
        @DisplayName("4.2: reenviosRestantes baja de 2 a 0 y P9: durante la espera no llega correo")
        void reenviosRestantes() throws Exception {
            String ip = nuevaIp();
            login(ip, "nuevo@styleradar.com", CLAVE);
            for (int esperado = 2; esperado >= 0; esperado--) {
                reenviar(ip, "nuevo@styleradar.com").andExpect(jsonPath("$.resendsRemaining").value(esperado));
            }
            String ip2 = nuevaIp();
            cuenta("otro@styleradar.com", 19, "BUYER", "ACTIVE", false);
            login(ip2, "otro@styleradar.com", CLAVE);
            for (int i = 0; i < 3; i++) {
                verificar(ip2, "otro@styleradar.com", "000000".equals(ultimoCodigo.get("otro@styleradar.com")) ? "111111" : "000000");
            }
            ultimoCodigo.remove("otro@styleradar.com");
            reenviar(ip2, "otro@styleradar.com").andExpect(status().isTooManyRequests());
            assertThat(ultimoCodigo).doesNotContainKey("otro@styleradar.com");
        }

        @Test
        @DisplayName("5 y P15: logout → 204; reutilizar el token o no enviarlo → 401 INVALID_TOKEN")
        void cierreDeSesion() throws Exception {
            String ip = nuevaIp();
            String token = tokenDe(login(ip, "verificado@styleradar.com", CLAVE));
            logout(ip, token).andExpect(status().isNoContent())
                    .andExpect(r -> assertThat(r.getResponse().getContentAsString()).isEmpty());
            error(logout(ip, token), 401, "INVALID_TOKEN", "El token presentado es inválido o expirado");
            error(logout(ip, null), 401, "INVALID_TOKEN", null);
            error(logout(ip, "no.es.jwt"), 401, "INVALID_TOKEN", null);
        }

        @Test
        @DisplayName("5.3: refresh entrega un token nuevo y revoca el anterior")
        void renovacion() throws Exception {
            String ip = nuevaIp();
            String token = tokenDe(login(ip, "verificado@styleradar.com", CLAVE));
            ResultActions r = llamar(ip, "/refresh", Map.of("refreshToken", token)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.expiresIn").value(43200));
            String nuevo = tokenDe(r);
            error(logout(ip, token), 401, "INVALID_TOKEN", null);
            logout(ip, nuevo).andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("6 y P13: Google entrega la sesión completa y luego con contraseña entra sin código")
        void googleYLuegoContrasena() throws Exception {
            when(decodificadorGoogle.decode(any())).thenReturn(tokenGoogle("google@styleradar.com", true));
            llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "id-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.expiresIn").value(43200))
                    .andExpect(jsonPath("$.userId").value(17))
                    .andExpect(jsonPath("$.email").value("google@styleradar.com"))
                    .andExpect(jsonPath("$.emailVerified").value(true))
                    .andExpect(jsonPath("$.otpExpiresInSeconds").value(nullValue()))
                    .andExpect(jsonPath("$.otpAttemptsRemaining").value(0));
            login(nuevaIp(), "google@styleradar.com", CLAVE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.otpRequired").value(false))
                    .andExpect(jsonPath("$.accessToken").isNotEmpty());
            assertThat(ultimoCodigo).isEmpty();
        }

        @Test
        @DisplayName("B10 y G3: CORS permite http://localhost:5173 y expone Retry-After")
        void cors() throws Exception {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type,authorization"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
            String expuestas = mockMvc.perform(post("/api/v1/auth/login").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                            .with(req -> {
                                req.setRemoteAddr(nuevaIp());
                                return req;
                            })
                            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x@y.co\",\"password\":\"" + CLAVE + "\"}"))
                    .andReturn().getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
            assertThat(expuestas).containsIgnoringCase("Retry-After");
        }
    }

    // ---------------------------------------------------------------- Análisis de requerimientos v2 (17/11/2026)

    @Nested
    @DisplayName("Análisis v2: nombres en inglés, mensajes en español")
    class AnalisisV2 {

        private JsonNode json(ResultActions r) throws Exception {
            return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("Salida de login: exactamente los campos del documento, sin verificationPending")
        void camposDeSalida() throws Exception {
            JsonNode b = json(login(nuevaIp(), "verificado@styleradar.com", CLAVE));
            assertThat(b.fieldNames()).toIterable().containsExactlyInAnyOrder("accessToken", "tokenType", "expiresIn",
                    "userId", "roles", "email", "emailVerified", "otpRequired", "otpExpiresInSeconds",
                    "otpAttemptsRemaining", "message");
            assertThat(b.get("message").asText()).isEqualTo("Inicio de sesión exitoso");
            JsonNode r = json(login(nuevaIp(), "nuevo@styleradar.com", CLAVE));
            assertThat(r.get("message").asText()).isEqualTo("Código de verificación OTP enviado a su correo electrónico");
            JsonNode reenvio = json(reenviar(nuevaIp(), "nuevo@styleradar.com"));
            assertThat(reenvio.fieldNames()).toIterable().containsExactlyInAnyOrder("email", "message",
                    "expiresInSeconds", "attemptsRemaining", "resendsRemaining");
        }

        @Test
        @DisplayName("Roles en inglés en la respuesta y en el JWT (BUYER, STORE_ADMIN, FOUNDATION_REPRESENTATIVE, STYLERADAR_ADMIN)")
        void rolesEnIngles() throws Exception {
            cuenta("legado@styleradar.com", 20, "COMPRADOR", "ACTIVE", true);
            JsonNode b = json(login(nuevaIp(), "legado@styleradar.com", CLAVE));
            assertThat(b.get("roles").get(0).asText()).isEqualTo("BUYER");
            JsonNode claims = objectMapper.readTree(java.util.Base64.getUrlDecoder()
                    .decode(b.get("accessToken").asText().split("\\.")[1]));
            assertThat(claims.get("roles").get(0).asText()).isEqualTo("BUYER");
            cuenta("admin@styleradar.com", 21, "STYLERADAR_ADMIN", "ACTIVE", true);
            assertThat(json(login(nuevaIp(), "admin@styleradar.com", CLAVE)).get("roles").get(0).asText())
                    .isEqualTo("STYLERADAR_ADMIN");
        }

        @Test
        @DisplayName("FA-05: contraseña de 8 a 30 caracteres")
        void contrasenaDe8a30() throws Exception {
            ResultActions r = login(nuevaIp(), "verificado@styleradar.com", "A".repeat(31));
            error(r, 400, "INVALID_DATA", INVALID_DATA);
            r.andExpect(jsonPath("$.errors.password").value("La contraseña debe tener entre 8 y 30 caracteres"));
            login(nuevaIp(), "verificado@styleradar.com", "A".repeat(30)).andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("ProblemDetail: type /errors/{code}, title, code, errors, attemptsRemaining y retryAfterSeconds")
        void problemDetail() throws Exception {
            ResultActions r = login(nuevaIp(), "verificado@styleradar.com", "OtraClave2026*");
            JsonNode e = json(r);
            assertThat(e.get("type").asText()).isEqualTo("https://api.styleradar.com/errors/invalid-credentials");
            assertThat(e.get("title").asText()).isEqualTo("Unauthorized");
            assertThat(e.get("code").asText()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(e.get("detail").asText()).isEqualTo("El correo o la contraseña son incorrectos");
            assertThat(e.get("instance").asText()).isEqualTo("/api/v1/auth/login");
            assertThat(e.fieldNames()).toIterable().contains("errors", "attemptsRemaining", "retryAfterSeconds",
                    "timestamp", "error", "message", "path").doesNotContain("codigo", "validationErrors",
                    "intentosRestantes", "esperarSegundos");
            r.andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/problem+json"));
        }

        @Test
        @DisplayName("El código OTP se envía como otpCode")
        void otpCode() throws Exception {
            String ip = nuevaIp();
            login(ip, "nuevo@styleradar.com", CLAVE);
            llamar(ip, "/otp/verify", Map.of("email", "nuevo@styleradar.com",
                    "otpCode", ultimoCodigo.get("nuevo@styleradar.com"))).andExpect(status().isOk());
            ResultActions r = llamar(nuevaIp(), "/otp/verify", Map.of("email", "nuevo@styleradar.com"));
            r.andExpect(jsonPath("$.errors.otpCode").value("El código OTP es obligatorio"));
        }

        @Test
        @DisplayName("Evento user.status.changed de ms-usuarios (v2) se lee con sus nombres en inglés")
        void eventoV2() throws Exception {
            var evento = objectMapper.readValue("{\"eventId\":\"e-1\",\"userId\":12,\"previousStatus\":\"ACTIVE\","
                    + "\"newStatus\":\"BLOCKED\",\"changedBy\":1,\"reason\":\"Fraude\",\"changedAt\":\"2026-11-17T10:00:00Z\"}",
                    edu.dosw.styleradar.auth.application.dto.event.UsuarioEstadoCambiadoEventDTO.class);
            assertThat(evento.eventoId()).isEqualTo("e-1");
            assertThat(evento.usuarioId()).isEqualTo(12L);
            assertThat(evento.estadoNuevo().requiereRevocarSesiones()).isTrue();
        }
    }
}
