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
 * Trazabilidad del análisis de requerimientos de ms-auth (StyleRadar_Requerimientos_ms-auth_CADche, 13/11/2026).
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
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Trazabilidad del Word de ms-auth (SR-RF-02 y SR-RF-92)")
class TrazabilidadRequerimientosAuthTest {

    private static final String CLAVE = "StyleRadar2026*";
    private static final String DATOS_INVALIDOS = "La solicitud contiene datos inválidos";
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
        cuenta("verificado@styleradar.com", 12, "COMPRADOR", "ACTIVO", true);
        cuenta("nuevo@styleradar.com", 13, "ADMIN_ALMACEN", "ACTIVO", false);
        cuenta("bloqueado@styleradar.com", 14, "COMPRADOR", "BLOQUEADO", true);
        cuenta("inactivo@styleradar.com", 15, "COMPRADOR", "INACTIVO", true);
        cuenta("caido@styleradar.com", 16, "COMPRADOR", "ACTIVO", true);
        cuenta("google@styleradar.com", 17, "REPRESENTANTE_FUNDACION", "ACTIVO", false);
        cuenta("googlebloqueado@styleradar.com", 18, "COMPRADOR", "BLOQUEADO", true);
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
                "id", id, "email", email, "rol", rol, "estado", estado, "correoVerificado", verificado)));
    }

    private static void iniciarMsUsuarios() throws IOException {
        if (msUsuarios != null) {
            return;
        }
        msUsuarios = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        ObjectMapper json = new ObjectMapper();
        msUsuarios.createContext("/internal/api/v1/usuarios/verificar-credenciales", seguro(ex -> {
            JsonNode cuerpo = json.readTree(ex.getRequestBody());
            Map<String, Object> c = CUENTAS.get(cuerpo.path("email").asText());
            if (c != null && "caido@styleradar.com".equals(c.get("email"))) {
                responder(ex, 500, "{}");
            } else if (c == null || !CLAVE.equals(cuerpo.path("password").asText())) {
                responder(ex, 401, "{}");
            } else if (!"ACTIVO".equals(c.get("estado"))) {
                responder(ex, 403, "{}");
            } else {
                responder(ex, 200, json.writeValueAsString(c));
            }
        }));
        msUsuarios.createContext("/internal/api/v1/usuarios/oauth2", seguro(ex -> {
            JsonNode cuerpo = json.readTree(ex.getRequestBody());
            Map<String, Object> c = CUENTAS.get(cuerpo.path("email").asText());
            responder(ex, c == null ? 404 : 200, c == null ? "{}" : json.writeValueAsString(c));
        }));
        msUsuarios.createContext("/internal/api/v1/usuarios/", seguro(ex -> {
            String[] partes = ex.getRequestURI().getPath().split("/");
            if ("PATCH".equals(ex.getRequestMethod()) && ex.getRequestURI().getPath().endsWith("/verificar-correo")) {
                long id = Long.parseLong(partes[partes.length - 2]);
                CORREOS_VERIFICADOS.add(id);
                CUENTAS.values().stream().filter(c -> ((Number) c.get("id")).longValue() == id)
                        .forEach(c -> c.put("correoVerificado", true));
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
        return llamar(ip, "/otp/verificar", Map.of("email", email, "codigoOtp", codigo));
    }

    private ResultActions reenviar(String ip, String email) throws Exception {
        return llamar(ip, "/otp/reenviar", Map.of("email", email));
    }

    /** Formato único de error del Word: código HTTP, código de error, mensaje y fecha AAAA-MM-DDThh:mm:ss. */
    private static void error(ResultActions r, int http, String codigo, String mensaje) throws Exception {
        r.andExpect(status().is(http))
                .andExpect(jsonPath("$.codigo").value(codigo))
                .andExpect(jsonPath("$.status").value(http))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")));
        if (mensaje != null) {
            r.andExpect(jsonPath("$.message").value(mensaje));
        }
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
                    .andExpect(jsonPath("$.expiresIn").value(86400))
                    .andExpect(jsonPath("$.usuarioId").value(12))
                    .andExpect(jsonPath("$.roles[0]").value("COMPRADOR"))
                    .andExpect(jsonPath("$.email").value("verificado@styleradar.com"))
                    .andExpect(jsonPath("$.correoVerificado").value(true))
                    .andExpect(jsonPath("$.requiereOtp").value(false))
                    .andExpect(jsonPath("$.verificacionPendiente").value(false));
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
            error(r, 400, "DATOS_INVALIDOS", DATOS_INVALIDOS);
            r.andExpect(jsonPath("$.validationErrors.email").value("El correo electrónico es obligatorio"));
        }

        @Test
        @DisplayName("FA-02: correo con formato inválido → 400")
        void fa02() throws Exception {
            ResultActions r = login(nuevaIp(), "correo-sin-arroba", CLAVE);
            error(r, 400, "DATOS_INVALIDOS", DATOS_INVALIDOS);
            r.andExpect(jsonPath("$.validationErrors.email").value("El formato del correo electrónico es inválido"));
        }

        @Test
        @DisplayName("FA-03: correo de más de 254 caracteres → 400")
        void fa03() throws Exception {
            ResultActions r = login(nuevaIp(), "a".repeat(250) + "@x.co", CLAVE);
            error(r, 400, "DATOS_INVALIDOS", DATOS_INVALIDOS);
            r.andExpect(jsonPath("$.validationErrors.email")
                    .value("El correo electrónico excede la longitud máxima permitida"));
        }

        @Test
        @DisplayName("FA-04: contraseña vacía → 400 «La contraseña es obligatoria»")
        void fa04() throws Exception {
            ResultActions r = login(nuevaIp(), "verificado@styleradar.com", "");
            error(r, 400, "DATOS_INVALIDOS", DATOS_INVALIDOS);
            r.andExpect(jsonPath("$.validationErrors.password").value("La contraseña es obligatoria"));
        }

        @Test
        @DisplayName("FA-05: contraseña de menos de 8 o más de 72 caracteres → 400")
        void fa05() throws Exception {
            for (String clave : new String[] {"1234567", "x".repeat(73)}) {
                ResultActions r = login(nuevaIp(), "verificado@styleradar.com", clave);
                error(r, 400, "DATOS_INVALIDOS", DATOS_INVALIDOS);
                r.andExpect(jsonPath("$.validationErrors.password")
                        .value("La contraseña debe tener entre 8 y 72 caracteres"));
            }
        }

        @Test
        @DisplayName("FA-06 y RN-04: credenciales incorrectas → 401 con el mismo mensaje si falla el correo o la clave")
        void fa06() throws Exception {
            error(login(nuevaIp(), "verificado@styleradar.com", "Incorrecta1*"),
                    401, "CREDENCIALES_INCORRECTAS", "El correo o la contraseña son incorrectos");
            error(login(nuevaIp(), "noexiste@styleradar.com", CLAVE),
                    401, "CREDENCIALES_INCORRECTAS", "El correo o la contraseña son incorrectos");
        }

        @Test
        @DisplayName("FA-07 y RN-03: cuenta Inactiva o Bloqueada → 403")
        void fa07() throws Exception {
            for (String email : new String[] {"bloqueado@styleradar.com", "inactivo@styleradar.com"}) {
                error(login(nuevaIp(), email, CLAVE),
                        403, "CUENTA_NO_ACTIVA", "La cuenta se encuentra inactiva o bloqueada");
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
            error(r, 429, "LIMITE_SOLICITUDES", DEMASIADAS);
            r.andExpect(header().exists(HttpHeaders.RETRY_AFTER));
        }

        @Test
        @DisplayName("FA-09: ms-usuarios o el correo no disponibles → 503")
        void fa09() throws Exception {
            error(login(nuevaIp(), "caido@styleradar.com", CLAVE), 503, "SERVICIO_NO_DISPONIBLE", NO_DISPONIBLE);
            doThrow(new ServicioExternoNoDisponibleException("SMTP caído"))
                    .when(emailService).enviarOtp(anyString(), anyString());
            error(login(nuevaIp(), "nuevo@styleradar.com", CLAVE), 503, "SERVICIO_NO_DISPONIBLE", NO_DISPONIBLE);
        }

        @Test
        @DisplayName("FA-10 y RN-06: Google válido entra sin código y el correo queda verificado")
        void fa10() throws Exception {
            when(decodificadorGoogle.decode(any())).thenReturn(tokenGoogle("google@styleradar.com", true));
            llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "id-token"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").isNotEmpty())
                    .andExpect(jsonPath("$.requiereOtp").value(false))
                    .andExpect(jsonPath("$.roles[0]").value("REPRESENTANTE_FUNDACION"));
            assertThat(CORREOS_VERIFICADOS).contains(17L);
            assertThat(ultimoCodigo).isEmpty();
        }

        @Test
        @DisplayName("FA-11: ID token de Google inválido o con correo sin verificar → 401")
        void fa11() throws Exception {
            when(decodificadorGoogle.decode(any())).thenThrow(new BadJwtException("firma inválida"));
            error(llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "malo")), 401, "CREDENCIALES_INCORRECTAS",
                    "El ID Token de Google es inválido, expiró o no pertenece a StyleRadar");
            reset(decodificadorGoogle);
            when(decodificadorGoogle.decode(any())).thenReturn(tokenGoogle("google@styleradar.com", false));
            error(llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "sin-verificar")), 401,
                    "CREDENCIALES_INCORRECTAS", "La cuenta de Google no tiene el correo electrónico verificado");
        }

        @Test
        @DisplayName("FA-12: con Google, cuenta de StyleRadar bloqueada → 403 «Tu cuenta está {estado}…»")
        void fa12() throws Exception {
            when(decodificadorGoogle.decode(any())).thenReturn(tokenGoogle("googlebloqueado@styleradar.com", true));
            error(llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "id-token")), 403, "CUENTA_NO_ACTIVA",
                    "Tu cuenta está BLOQUEADA. Contacta al soporte de StyleRadar.");
        }

        @Test
        @DisplayName("FA-13: correo sin verificar → 200 sin token, verificación pendiente, 300 s y 3 intentos")
        void fa13() throws Exception {
            login(nuevaIp(), "nuevo@styleradar.com", CLAVE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value(nullValue()))
                    .andExpect(jsonPath("$.correoVerificado").value(false))
                    .andExpect(jsonPath("$.requiereOtp").value(true))
                    .andExpect(jsonPath("$.verificacionPendiente").value(true))
                    .andExpect(jsonPath("$.otpExpiraEnSegundos").value(300))
                    .andExpect(jsonPath("$.intentosOtpRestantes").value(3));
            assertThat(ultimoCodigo.get("nuevo@styleradar.com")).matches("\\d{6}");
        }

        @Test
        @DisplayName("FA-14: con Google, Google o ms-usuarios no disponibles → 503")
        void fa14() throws Exception {
            when(decodificadorGoogle.decode(any())).thenReturn(tokenGoogle("noregistrado@styleradar.com", true));
            error(llamar(nuevaIp(), "/oauth2/google", Map.of("idToken", "id-token")),
                    503, "SERVICIO_NO_DISPONIBLE", NO_DISPONIBLE);
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
            error(r, 429, "OTP_REENVIO_BLOQUEADO", null);
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
                    .andExpect(jsonPath("$.correoVerificado").value(true))
                    .andExpect(jsonPath("$.roles[0]").value("ADMIN_ALMACEN"))
                    .andExpect(jsonPath("$.email").value("nuevo@styleradar.com"));
            assertThat(CORREOS_VERIFICADOS).contains(13L);
            error(verificar(ip, "nuevo@styleradar.com", codigo), 401, "OTP_VENCIDO",
                    "El código OTP expiró o no existe. Debe solicitar uno nuevo.");
            login(ip, "nuevo@styleradar.com", CLAVE).andExpect(jsonPath("$.requiereOtp").value(false));
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
            error(r, 400, "DATOS_INVALIDOS", DATOS_INVALIDOS);
            r.andExpect(jsonPath("$.validationErrors.codigoOtp").value("El código OTP es obligatorio"));
        }

        @Test
        @DisplayName("FA-02: código sin exactamente 6 dígitos → 400")
        void fa02() throws Exception {
            for (String codigo : new String[] {"12345", "1234567", "12a456"}) {
                ResultActions r = verificar(ip, "nuevo@styleradar.com", codigo);
                error(r, 400, "DATOS_INVALIDOS", DATOS_INVALIDOS);
                r.andExpect(jsonPath("$.validationErrors.codigoOtp")
                        .value("El código OTP debe contener exactamente 6 dígitos numéricos"));
            }
        }

        @Test
        @DisplayName("FA-03: correo vacío o inválido al verificar → 400")
        void fa03() throws Exception {
            verificar(ip, "", "123456")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.email").value("El correo electrónico es obligatorio"));
            verificar(ip, "sin-arroba", "123456")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.validationErrors.email").value("El formato del correo electrónico es inválido"));
        }

        @Test
        @DisplayName("FA-04: código incorrecto con intentos → 401 con intentosRestantes 2 y luego 1")
        void fa04() throws Exception {
            ResultActions r = verificar(ip, "nuevo@styleradar.com", codigoIncorrecto());
            error(r, 401, "OTP_INCORRECTO", "El código OTP es incorrecto. Intentos restantes: 2");
            r.andExpect(jsonPath("$.intentosRestantes").value(2));
            verificar(ip, "nuevo@styleradar.com", codigoIncorrecto())
                    .andExpect(jsonPath("$.intentosRestantes").value(1));
        }

        @Test
        @DisplayName("FA-05 y RN-04: tercer código incorrecto → 429, 60 s de espera y el código se anula")
        void fa05() throws Exception {
            String correcto = ultimoCodigo.get("nuevo@styleradar.com");
            verificar(ip, "nuevo@styleradar.com", codigoIncorrecto());
            verificar(ip, "nuevo@styleradar.com", codigoIncorrecto());
            ResultActions r = verificar(ip, "nuevo@styleradar.com", codigoIncorrecto());
            error(r, 429, "OTP_INTENTOS_AGOTADOS",
                    "Se agotaron los intentos. Espere 60 segundos y solicite un nuevo código.");
            r.andExpect(jsonPath("$.esperarSegundos").value(60))
                    .andExpect(jsonPath("$.intentosRestantes").value(0))
                    .andExpect(header().string(HttpHeaders.RETRY_AFTER, "60"));
            error(verificar(ip, "nuevo@styleradar.com", correcto), 401, "OTP_VENCIDO", null);
        }

        @Test
        @DisplayName("FA-06: código vencido o anulado → 401 «El código OTP expiró o no existe…»")
        void fa06() throws Exception {
            redis.delete("otp:nuevo@styleradar.com");
            error(verificar(ip, "nuevo@styleradar.com", "123456"), 401, "OTP_VENCIDO",
                    "El código OTP expiró o no existe. Debe solicitar uno nuevo.");
        }

        @Test
        @DisplayName("FA-07 y RN-03: pedir código nuevo anula el anterior; respuesta con 300 s, 3 intentos y reenvíos restantes")
        void fa07() throws Exception {
            String anterior = ultimoCodigo.get("nuevo@styleradar.com");
            reenviar(ip, "nuevo@styleradar.com")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mensaje").value("Se envió un código de verificación de 6 dígitos a su correo registrado"))
                    .andExpect(jsonPath("$.email").value("nuevo@styleradar.com"))
                    .andExpect(jsonPath("$.expiraEnSegundos").value(300))
                    .andExpect(jsonPath("$.intentosRestantes").value(3))
                    .andExpect(jsonPath("$.reenviosRestantes").value(2));
            String nuevo = ultimoCodigo.get("nuevo@styleradar.com");
            if (!nuevo.equals(anterior)) {
                error(verificar(ip, "nuevo@styleradar.com", anterior), 401, "OTP_INCORRECTO", null);
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
            error(r, 429, "OTP_REENVIO_BLOQUEADO", null);
            r.andExpect(jsonPath("$.message").value(matchesPattern("Debe esperar \\d+ segundos para solicitar un nuevo código\\.")))
                    .andExpect(jsonPath("$.esperarSegundos").isNumber());
            redis.delete("otp:bloqueo:nuevo@styleradar.com");
            reenviar(ip, "nuevo@styleradar.com").andExpect(status().isOk());
        }

        @Test
        @DisplayName("FA-09: código nuevo sin verificación en curso → 401")
        void fa09() throws Exception {
            error(reenviar(ip, "sinsesion@styleradar.com"), 401, "SESION_OTP_INEXISTENTE",
                    "No se encontró una sesión activa o el tiempo de espera expiró. Inicie sesión nuevamente.");
        }

        @Test
        @DisplayName("FA-10 y RN-05: más de 3 códigos nuevos en la misma verificación → 429")
        void fa10() throws Exception {
            for (int i = 0; i < 3; i++) {
                reenviar(ip, "nuevo@styleradar.com").andExpect(status().isOk());
            }
            error(reenviar(ip, "nuevo@styleradar.com"), 429, "REENVIOS_AGOTADOS",
                    "Se superó el máximo de reenvíos permitidos para esta sesión. Inicie sesión nuevamente.");
        }

        @Test
        @DisplayName("FA-11: más de 15 solicitudes de verificación en un minuto → 429")
        void fa11() throws Exception {
            for (int i = 0; i < 15; i++) {
                verificar(ip, "nuevo@styleradar.com", "12a456");
            }
            error(verificar(ip, "nuevo@styleradar.com", "12a456"), 429, "LIMITE_SOLICITUDES", DEMASIADAS);
        }

        @Test
        @DisplayName("FA-12: el correo no está disponible al pedir código nuevo → 503 y el correo no queda verificado")
        void fa12() throws Exception {
            doThrow(new ServicioExternoNoDisponibleException("SMTP caído"))
                    .when(emailService).enviarOtp(eq("nuevo@styleradar.com"), anyString());
            error(reenviar(ip, "nuevo@styleradar.com"), 503, "SERVICIO_NO_DISPONIBLE", NO_DISPONIBLE);
            assertThat(CORREOS_VERIFICADOS).doesNotContain(13L);
        }

        @Test
        @DisplayName("FA-13: cierra sin verificar → el siguiente login vuelve a enviar un código nuevo")
        void fa13() throws Exception {
            String primero = ultimoCodigo.remove("nuevo@styleradar.com");
            login(ip, "nuevo@styleradar.com", CLAVE)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.requiereOtp").value(true));
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
}
