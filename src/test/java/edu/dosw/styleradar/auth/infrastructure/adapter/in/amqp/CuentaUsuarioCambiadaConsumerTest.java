package edu.dosw.styleradar.auth.infrastructure.adapter.in.amqp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import edu.dosw.styleradar.auth.application.dto.event.CuentaUsuarioCambiadaEventDTO;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.application.service.SesionesActivasService;
import edu.dosw.styleradar.auth.application.usecase.RevocarSesionesUsuarioUseCaseImpl;

@DisplayName("Consumidor de contraseña cambiada y cuenta eliminada → revocación de todas las sesiones")
class CuentaUsuarioCambiadaConsumerTest {

    private static final Instant AHORA = Instant.parse("2026-11-25T21:00:00Z");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    private final OtpService otp = mock(OtpService.class);
    private final SesionesActivasService sesiones = mock(SesionesActivasService.class);
    private final CuentaUsuarioCambiadaConsumer consumer = new CuentaUsuarioCambiadaConsumer(
            new RevocarSesionesUsuarioUseCaseImpl(otp, Clock.fixed(AHORA, ZoneOffset.UTC), sesiones));

    @Test
    void leeElEventoDeContrasenaCambiadaDeMsUsuarios() throws Exception {
        CuentaUsuarioCambiadaEventDTO e = json.readValue("{\"eventId\":\"evt-1\",\"eventType\":\"user.password.changed\","
                + "\"userId\":12,\"changedAt\":\"2026-11-25T20:00:00Z\"}", CuentaUsuarioCambiadaEventDTO.class);
        assertThat(e).isEqualTo(new CuentaUsuarioCambiadaEventDTO("evt-1", "user.password.changed", 12L,
                Instant.parse("2026-11-25T20:00:00Z")));
    }

    @Test
    void leeElEventoDeCuentaEliminadaSinEventId() throws Exception {
        CuentaUsuarioCambiadaEventDTO e = json.readValue("{\"userId\":13,\"deletedAt\":\"2026-11-25T20:30:00Z\"}",
                CuentaUsuarioCambiadaEventDTO.class);
        assertThat(e).isEqualTo(new CuentaUsuarioCambiadaEventDTO(null, "user.deleted", 13L,
                Instant.parse("2026-11-25T20:30:00Z")));
    }

    @Test
    void contrasenaCambiadaRevocaYMarcaElEventoComoProcesado() {
        consumer.recibir(new CuentaUsuarioCambiadaEventDTO("evt-1", "user.password.changed", 12L,
                Instant.parse("2026-11-25T20:00:00Z")));

        verify(otp).revocarSesionesUsuario(12L, Instant.parse("2026-11-25T20:00:00Z"));
        verify(sesiones).revocarUsuario(12L);
        verify(otp).registrarEventoProcesado("evt-1");
    }

    @Test
    void cuentaEliminadaSinFechaUsaElRelojYSinEventIdNoMarcaIdempotencia() {
        consumer.recibir(new CuentaUsuarioCambiadaEventDTO(null, null, 13L, null));

        verify(otp).revocarSesionesUsuario(13L, AHORA);
        verify(sesiones).revocarUsuario(13L);
        verify(otp, never()).registrarEventoProcesado(anyString());
    }

    @Test
    void eventoDuplicadoNuloOSinUsuarioNoRevoca() {
        when(otp.eventoYaProcesado("evt-dup")).thenReturn(true);
        consumer.recibir(new CuentaUsuarioCambiadaEventDTO("evt-dup", "user.password.changed", 12L, AHORA));
        consumer.recibir(null);
        consumer.recibir(new CuentaUsuarioCambiadaEventDTO("evt-2", "user.deleted", null, AHORA));

        verify(otp, never()).revocarSesionesUsuario(anyLong(), any());
        verify(sesiones, never()).revocarUsuario(anyLong());
    }
}
