package edu.dosw.styleradar.auth.infrastructure.adapter.in.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import edu.dosw.styleradar.auth.domain.event.UsuarioEstadoCambiadoEvent;
import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;

@DisplayName("Pruebas Unitarias de UsuarioEstadoCambiadoConsumer - OWASP A07 / A09:2025")
class UsuarioEstadoCambiadoConsumerTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UsuarioEstadoCambiadoConsumer consumer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        consumer = new UsuarioEstadoCambiadoConsumer(redisTemplate);
    }

    @Test
    @DisplayName("Debe guardar marca temporal de revocación cuando el usuario pasa a BLOQUEADA o INACTIVA")
    void debeRevocarSesionesEnEstadoBloqueadaEInactiva() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Instant now = Instant.ofEpochMilli(1700000000000L);

        consumer.recibir(new UsuarioEstadoCambiadoEvent(
                10L, EstadoCuenta.ACTIVA, EstadoCuenta.BLOQUEADA, 1L, "Abuso", now));
        verify(valueOperations).set("usuario:revocado:10", "1700000000000");

        consumer.recibir(new UsuarioEstadoCambiadoEvent(
                11L, EstadoCuenta.ACTIVA, EstadoCuenta.INACTIVA, 1L, "Baja", null));
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq("usuario:revocado:11"), anyString());
    }

    @Test
    @DisplayName("No debe registrar revocación cuando el evento es nulo, incompleto o estado ACTIVA")
    void noDebeRevocarEnEventosActivosOIncompletos() {
        consumer.recibir(null);
        consumer.recibir(new UsuarioEstadoCambiadoEvent(
                null, EstadoCuenta.ACTIVA, EstadoCuenta.BLOQUEADA, 1L, "x", Instant.now()));
        consumer.recibir(new UsuarioEstadoCambiadoEvent(
                12L, EstadoCuenta.BLOQUEADA, EstadoCuenta.ACTIVA, 1L, "Reactivado", Instant.now()));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("Debe propagar DataAccessException cuando Redis falla para no perder revocación silenciosamente")
    void debePropagarDataAccessExceptionSiRedisFalla() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("Redis down"))
                .when(valueOperations).set(anyString(), anyString());

        UsuarioEstadoCambiadoEvent event = new UsuarioEstadoCambiadoEvent(
                15L, EstadoCuenta.ACTIVA, EstadoCuenta.BLOQUEADA, 1L, "Fraude", Instant.now());

        assertThatThrownBy(() -> consumer.recibir(event))
                .isInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("Debe aceptar variantes masculinas ACTIVO/INACTIVO/BLOQUEADO del contrato de eventos RabbitMQ")
    void debeAceptarVariantesEstadoCuentaDelContratoRabbitMQ() {
        org.assertj.core.api.Assertions.assertThat(EstadoCuenta.fromValue("ACTIVO")).isEqualTo(EstadoCuenta.ACTIVA);
        org.assertj.core.api.Assertions.assertThat(EstadoCuenta.fromValue("INACTIVO")).isEqualTo(EstadoCuenta.INACTIVA);
        org.assertj.core.api.Assertions.assertThat(EstadoCuenta.fromValue("BLOQUEADO")).isEqualTo(EstadoCuenta.BLOQUEADA);
        org.assertj.core.api.Assertions.assertThat(EstadoCuenta.fromValue("   ")).isNull();
    }
}

