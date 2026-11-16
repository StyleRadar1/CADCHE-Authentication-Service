package edu.dosw.styleradar.auth.infrastructure.adapter.in.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.dto.event.UsuarioEstadoCambiadoEventDTO;
import edu.dosw.styleradar.auth.application.service.impl.OtpServiceImpl;
import edu.dosw.styleradar.auth.application.usecase.RevocarSesionesUsuarioUseCaseImpl;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.EstadoCuenta;

@DisplayName("Consumidor AMQP UsuarioEstadoCambiadoEvent → caso de uso de revocación global de sesiones")
class UsuarioEstadoCambiadoConsumerTest {

    private static final Instant AHORA = Instant.ofEpochMilli(1700000000000L);

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private UsuarioEstadoCambiadoConsumer consumer;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        consumer = new UsuarioEstadoCambiadoConsumer(new RevocarSesionesUsuarioUseCaseImpl(
                new OtpServiceImpl(redisTemplate), Clock.fixed(AHORA, ZoneOffset.UTC)));
    }

    private static UsuarioEstadoCambiadoEventDTO evento(String id, Long usuarioId, EstadoCuenta nuevo, Instant fecha) {
        return new UsuarioEstadoCambiadoEventDTO(id, usuarioId, EstadoCuenta.ACTIVA, nuevo, 1L, "motivo", fecha);
    }

    @Test
    @DisplayName("Debe guardar marca temporal de revocación cuando el usuario pasa a BLOQUEADA o INACTIVA")
    void debeRevocarSesionesEnEstadoBloqueadaEInactiva() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        consumer.recibir(evento(null, 10L, EstadoCuenta.BLOQUEADA, AHORA));
        verify(valueOperations).set("usuario:revocado:10", "1700000000000");

        consumer.recibir(evento(null, 11L, EstadoCuenta.INACTIVA, null));
        verify(valueOperations).set("usuario:revocado:11", "1700000000000");
    }

    @Test
    @DisplayName("No debe registrar revocación cuando el evento es nulo, incompleto o estado ACTIVA")
    void noDebeRevocarEnEventosActivosOIncompletos() {
        consumer.recibir(null);
        consumer.recibir(evento(null, null, EstadoCuenta.BLOQUEADA, AHORA));
        consumer.recibir(evento(null, 12L, EstadoCuenta.ACTIVA, AHORA));
        consumer.recibir(evento(null, 13L, null, AHORA));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("Debe propagar el fallo de Redis (fail-closed) para que RabbitMQ reintente y envíe a la DLQ")
    void debePropagarFalloDeRedisParaReintentoYDlq() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new DataAccessResourceFailureException("Redis down"))
                .when(valueOperations).set(anyString(), anyString());

        UsuarioEstadoCambiadoEventDTO bloqueo = evento(null, 15L, EstadoCuenta.BLOQUEADA, AHORA);
        assertThatThrownBy(() -> consumer.recibir(bloqueo))
                .isInstanceOf(ServicioExternoNoDisponibleException.class)
                .hasCauseInstanceOf(DataAccessResourceFailureException.class);
    }

    @Test
    @DisplayName("Debe aceptar variantes masculinas ACTIVO/INACTIVO/BLOQUEADO del contrato de eventos RabbitMQ")
    void debeAceptarVariantesEstadoCuentaDelContratoRabbitMQ() {
        assertThat(EstadoCuenta.fromValue("ACTIVO")).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(EstadoCuenta.fromValue("inactivo")).isEqualTo(EstadoCuenta.INACTIVA);
        assertThat(EstadoCuenta.fromValue("BLOQUEADO")).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(EstadoCuenta.fromValue("BLOQUEADA")).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(EstadoCuenta.fromValue(null)).isNull();
        assertThat(EstadoCuenta.fromValue("   ")).isNull();
        assertThatThrownBy(() -> EstadoCuenta.fromValue("DESCONOCIDO")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Debe deserializar el JSON de ms-usuarios con alias nuevoEstado y estados en masculino")
    void debeDeserializarJsonDeMsUsuarios() throws Exception {
        String json = "{\"eventoId\":\"evt-1\",\"usuarioId\":7,\"estadoAnterior\":\"ACTIVO\","
                + "\"nuevoEstado\":\"BLOQUEADO\",\"administradorId\":1,\"motivo\":\"x\","
                + "\"fechaCambio\":\"2026-11-13T10:15:30Z\",\"campoExtra\":true}";

        UsuarioEstadoCambiadoEventDTO dto = new ObjectMapper().findAndRegisterModules()
                .readValue(json, UsuarioEstadoCambiadoEventDTO.class);

        assertThat(dto.estadoAnterior()).isEqualTo(EstadoCuenta.ACTIVA);
        assertThat(dto.estadoNuevo()).isEqualTo(EstadoCuenta.BLOQUEADA);
        assertThat(dto.fechaCambio()).isEqualTo(Instant.parse("2026-11-13T10:15:30Z"));
    }

    @Test
    @DisplayName("Debe descartar eventos duplicados cuando eventoId ya fue procesado en Redis")
    void debeDescartarEventosDuplicadosPorEventoId() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(eq("evento:procesado:evt-usr-999"), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true)
                .thenReturn(false);

        UsuarioEstadoCambiadoEventDTO conId = evento("evt-usr-999", 20L, EstadoCuenta.BLOQUEADA, AHORA);
        consumer.recibir(conId);
        consumer.recibir(conId);
        verify(valueOperations, times(1)).set("usuario:revocado:20", "1700000000000");

        consumer.recibir(evento("   ", 21L, EstadoCuenta.BLOQUEADA, AHORA));
        verify(valueOperations).set("usuario:revocado:21", "1700000000000");
    }
}
