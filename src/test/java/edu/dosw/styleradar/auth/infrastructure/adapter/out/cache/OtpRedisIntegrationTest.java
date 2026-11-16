package edu.dosw.styleradar.auth.infrastructure.adapter.out.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import edu.dosw.styleradar.auth.application.service.impl.OtpServiceImpl;
import edu.dosw.styleradar.auth.domain.exception.OtpBloqueadoTemporalmenteException;
import edu.dosw.styleradar.auth.domain.exception.OtpIntentosAgotadosException;
import edu.dosw.styleradar.auth.domain.exception.OtpInvalidoException;

/**
 * Integración contra Redis 7 real (Testcontainers): flujo completo de SR-RF-92 con la decisión D1.
 * Se omite automáticamente si la máquina no tiene Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("Integración Redis 7 · OtpServiceImpl (SR-RF-92, decisión D1)")
class OtpRedisIntegrationTest {

    private static final String CORREO = "integracion@styleradar.com";

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private OtpServiceImpl otpService;

    @BeforeAll
    static void conectar() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @AfterAll
    static void desconectar() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void limpiar() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        otpService = new OtpServiceImpl(redisTemplate);
        ReflectionTestUtils.setField(otpService, "lockoutSeconds", 2L);
    }

    @Test
    @DisplayName("3 fallos → espera obligatoria; login y reenvío bloqueados; tras la espera se reenvía sin nuevo login")
    void flujoCompletoDeBloqueoYReenvio() throws Exception {
        String codigo = otpService.generarOtp(CORREO, 77L, "COMPRADOR");
        String incorrecto = codigo.equals("000000") ? "111111" : "000000";

        assertThatThrownBy(() -> otpService.verificarOtp(CORREO, incorrecto))
                .isInstanceOf(OtpInvalidoException.class).extracting("intentosRestantes").isEqualTo(2);
        assertThatThrownBy(() -> otpService.verificarOtp(CORREO, incorrecto))
                .isInstanceOf(OtpInvalidoException.class).extracting("intentosRestantes").isEqualTo(1);
        assertThatThrownBy(() -> otpService.verificarOtp(CORREO, incorrecto))
                .isInstanceOf(OtpIntentosAgotadosException.class).extracting("esperarSegundos").isEqualTo(2L);

        assertThatThrownBy(() -> otpService.reenviarOtp(CORREO)).isInstanceOf(OtpBloqueadoTemporalmenteException.class);
        assertThatThrownBy(() -> otpService.generarOtp(CORREO, 77L, "COMPRADOR"))
                .isInstanceOf(OtpBloqueadoTemporalmenteException.class);
        assertThat(redisTemplate.getExpire("otp:usuario:" + CORREO)).isBetween(890L, 900L);

        Thread.sleep(2_200L);

        String nuevo = otpService.reenviarOtp(CORREO);
        otpService.verificarOtp(CORREO, nuevo);
        assertThat(otpService.obtenerUsuarioId(CORREO)).isEqualTo(77L);
        assertThat(otpService.obtenerReenviosRestantes(CORREO)).isEqualTo(2);
    }

    @Test
    @DisplayName("Lista negra JWT, revocación global e idempotencia de eventos sobre Redis real")
    void revocacionYIdempotencia() {
        otpService.invalidarToken("token-x", 30L);
        assertThat(otpService.esTokenInvalido("token-x")).isTrue();
        assertThat(otpService.esTokenInvalido("token-y")).isFalse();

        otpService.revocarSesionesUsuario(8L, java.time.Instant.ofEpochMilli(10_000L));
        assertThat(otpService.estaUsuarioRevocado(8L, 9_000L)).isTrue();
        assertThat(otpService.estaUsuarioRevocado(8L, 11_000L)).isFalse();

        assertThat(otpService.registrarEventoProcesado("evt-1")).isTrue();
        assertThat(otpService.registrarEventoProcesado("evt-1")).isFalse();
    }
}
