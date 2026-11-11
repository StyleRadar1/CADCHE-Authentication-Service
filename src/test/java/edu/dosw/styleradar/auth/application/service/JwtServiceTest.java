package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import edu.dosw.styleradar.auth.application.service.impl.JwtServiceImpl;
import edu.dosw.styleradar.auth.domain.model.Rol;

@DisplayName("Pruebas Unitarias de JwtService - SR-RF-02 / SR-RNF-01 / OWASP A04-A08:2025")
class JwtServiceTest {

    private JwtServiceImpl jwtService;

    private final String secretBase64 = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
    private final String duration = "PT24H";

    @BeforeEach
    void setUp() {
        jwtService = new JwtServiceImpl(secretBase64, duration);
    }

    @Test
    @DisplayName("Debe generar token JWT firmado con claims de roles y subject usuarioId")
    void debeGenerarTokenConSubjectYRoles() {
        Set<Rol> roles = Set.of(Rol.COMPRADOR, Rol.ADMIN_ALMACEN);
        String token = jwtService.generarToken(123L, roles);

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Debe validar exitosamente un token JWT recién generado")
    void debeValidarTokenExitosamente() {
        String token = jwtService.generarToken(99L, Set.of(Rol.COMPRADOR));

        assertThat(jwtService.validarToken(token)).isNotNull();
        assertThat(jwtService.validarToken(token).getSubject()).isEqualTo("99");
        assertThat(jwtService.validarToken(token).getIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("Debe rechazar tokens corruptos, nulos o con firma alterada (OWASP A08:2025)")
    void debeRechazarTokensInvalidosOAlterados() {
        String validToken = jwtService.generarToken(99L, Set.of(Rol.COMPRADOR));
        String[] parts = validToken.split("\\.");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"1\",\"roles\":[\"ADMIN_STYLERADAR\"]}".getBytes(StandardCharsets.UTF_8));
        String tamperedToken = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtService.validarToken(tamperedToken))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jwtService.validarToken("token.invalido.falso"))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jwtService.validarToken(""))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jwtService.validarToken(null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Debe rechazar secretos vacíos, no Base64 o menores a 256 bits (OWASP A04:2025)")
    void debeRechazarSecretosDebiles() {
        String shortSecret = Base64.getEncoder().encodeToString("demasiado-corto".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JwtServiceImpl("", duration))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtServiceImpl("no_es_base64!!!", duration))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtServiceImpl(shortSecret, duration))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("256 bits");
    }

    @Test
    @DisplayName("Debe rechazar el secreto de ejemplo de desarrollo en perfiles prod y qa (OWASP A04:2025)")
    void debeRechazarSecretoDeEjemploEnProdYQa() {
        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        MockEnvironment qaEnv = new MockEnvironment();
        qaEnv.setActiveProfiles("qa");

        assertThatThrownBy(() -> new JwtServiceImpl(secretBase64, duration, prodEnv))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtServiceImpl(secretBase64, duration, qaEnv))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Debe extraer correctamente el usuarioId y los roles desde el JWT")
    void debeExtraerUsuarioIdYRoles() {
        String token = jwtService.generarToken(456L, Set.of(Rol.REPRESENTANTE_FUNDACION));

        assertThat(jwtService.obtenerUsuarioId(token)).isEqualTo(456L);
        assertThat(jwtService.obtenerRoles(token)).containsExactly(Rol.REPRESENTANTE_FUNDACION);
    }

    @Test
    @DisplayName("Debe calcular el tiempo restante en segundos de un token válido")
    void debeCalcularTiempoRestanteSegundos() {
        String token = jwtService.generarToken(100L, Set.of(Rol.COMPRADOR));

        long tiempoRestante = jwtService.obtenerTiempoRestanteSegundos(token);

        assertThat(tiempoRestante).isGreaterThan(86300L).isLessThanOrEqualTo(86400L);
    }
}
