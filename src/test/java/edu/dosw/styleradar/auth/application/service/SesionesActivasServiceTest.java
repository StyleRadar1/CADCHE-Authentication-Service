package edu.dosw.styleradar.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import edu.dosw.styleradar.auth.domain.event.SesionCambiadaEvent;
import edu.dosw.styleradar.auth.domain.event.SesionCambiadaEvent.Accion;
import edu.dosw.styleradar.auth.domain.model.SesionActiva;
import edu.dosw.styleradar.auth.domain.port.out.EventoSesionPublisherPort;
import edu.dosw.styleradar.auth.domain.port.out.RegistroSesionesPort;
import io.jsonwebtoken.Jwts;

@DisplayName("user.session.changed: registro de sesiones y delta +1/-1")
class SesionesActivasServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-25T15:04:05Z");
    private static final Instant VENCE = AHORA.plusSeconds(43200);

    private JwtService jwt;
    private RegistroSesionesPort registro;
    private EventoSesionPublisherPort eventos;
    private SesionesActivasService servicio;

    @BeforeEach
    void setUp() {
        jwt = mock(JwtService.class);
        registro = mock(RegistroSesionesPort.class);
        eventos = mock(EventoSesionPublisherPort.class);
        servicio = new SesionesActivasService(jwt, registro, eventos, Clock.fixed(AHORA, ZoneOffset.UTC));
        when(jwt.validarToken("token-1")).thenReturn(Jwts.claims().id("jti-1").subject("12")
                .expiration(Date.from(VENCE)).build());
        when(jwt.validarToken("token-2")).thenReturn(Jwts.claims().id("jti-2").subject("12")
                .expiration(Date.from(VENCE.plusSeconds(600))).build());
    }

    private static SesionActiva sesion(String jti) {
        return new SesionActiva("sesion-1", 12L, jti, VENCE);
    }

    private SesionCambiadaEvent unico() {
        ArgumentCaptor<SesionCambiadaEvent> c = ArgumentCaptor.forClass(SesionCambiadaEvent.class);
        verify(eventos).publicarSesionCambiada(c.capture());
        return c.getValue();
    }

    @Test
    @DisplayName("Iniciar registra la sesión del token y publica LOGIN con delta +1")
    void iniciar() {
        servicio.iniciar("token-1");

        ArgumentCaptor<SesionActiva> s = ArgumentCaptor.forClass(SesionActiva.class);
        verify(registro).registrar(s.capture());
        assertThat(s.getValue().tokenId()).isEqualTo("jti-1");
        assertThat(s.getValue().usuarioId()).isEqualTo(12L);
        assertThat(s.getValue().expira()).isEqualTo(VENCE);
        assertThat(s.getValue().sesionId()).isNotBlank();
        SesionCambiadaEvent e = unico();
        assertThat(e.accion()).isEqualTo(Accion.LOGIN);
        assertThat(e.delta()).isEqualTo(1);
        assertThat(e.usuarioId()).isEqualTo(12L);
        assertThat(e.sesionId()).isEqualTo(s.getValue().sesionId());
        assertThat(e.fecha()).isEqualTo(AHORA);
        assertThat(e.eventoId()).isNotBlank();
    }

    @Test
    @DisplayName("Renovar traslada la sesión al token nuevo sin publicar nada")
    void renovar() {
        servicio.renovar("token-1", "token-2");

        verify(registro).reemplazar("jti-1", "jti-2", VENCE.plusSeconds(600));
        verify(eventos, never()).publicarSesionCambiada(any());
    }

    @Test
    @DisplayName("Cerrar publica LOGOUT -1 una sola vez")
    void cerrar() {
        when(registro.finalizar("jti-1")).thenReturn(Optional.of(sesion("jti-1"))).thenReturn(Optional.empty());

        servicio.cerrar("token-1");
        servicio.cerrar("token-1");

        SesionCambiadaEvent e = unico();
        assertThat(e.accion()).isEqualTo(Accion.LOGOUT);
        assertThat(e.delta()).isEqualTo(-1);
        assertThat(e.sesionId()).isEqualTo("sesion-1");
    }

    @Test
    @DisplayName("Revocar la cuenta publica REVOKED -1 por cada sesión abierta")
    void revocar() {
        when(registro.finalizarDeUsuario(12L)).thenReturn(List.of(sesion("jti-1"), sesion("jti-2")));

        servicio.revocarUsuario(12L);

        ArgumentCaptor<SesionCambiadaEvent> c = ArgumentCaptor.forClass(SesionCambiadaEvent.class);
        verify(eventos, times(2)).publicarSesionCambiada(c.capture());
        assertThat(c.getAllValues()).extracting(SesionCambiadaEvent::accion).containsOnly(Accion.REVOKED);
        assertThat(c.getAllValues()).extracting(SesionCambiadaEvent::delta).containsOnly(-1);
    }

    @Test
    @DisplayName("El barrido publica EXPIRED -1 por cada token vencido y devuelve cuántos cerró")
    void expirar() {
        when(registro.finalizarVencidas(AHORA, SesionesActivasService.LOTE_VENCIDAS))
                .thenReturn(List.of(sesion("jti-1")));

        assertThat(servicio.expirarVencidas(AHORA)).isEqualTo(1);

        SesionCambiadaEvent e = unico();
        assertThat(e.accion()).isEqualTo(Accion.EXPIRED);
        assertThat(e.delta()).isEqualTo(-1);
    }

    @Test
    @DisplayName("Si Redis falla, el login, el logout y el barrido no se interrumpen")
    void fallosNoInterrumpen() {
        doThrow(new IllegalStateException("redis caído")).when(registro).registrar(any());
        when(registro.finalizar(any())).thenThrow(new IllegalStateException("redis caído"));
        when(registro.finalizarVencidas(any(), anyInt())).thenThrow(new IllegalStateException("redis caído"));
        when(jwt.validarToken("roto")).thenThrow(new IllegalArgumentException("token roto"));

        assertThatCode(() -> servicio.iniciar("token-1")).doesNotThrowAnyException();
        assertThatCode(() -> servicio.cerrar("token-1")).doesNotThrowAnyException();
        assertThatCode(() -> servicio.renovar("roto", "token-2")).doesNotThrowAnyException();
        assertThat(servicio.expirarVencidas(AHORA)).isZero();
        verify(eventos, never()).publicarSesionCambiada(any());
        verify(registro, never()).reemplazar(eq("roto"), any(), any());
    }

    @Test
    @DisplayName("Sin registro o sin publicador el servicio queda inactivo y no toca nada")
    void inactivo() {
        SesionesActivasService sinPuertos = new SesionesActivasService(jwt, null, eventos, Clock.systemUTC());

        sinPuertos.iniciar("token-1");
        sinPuertos.renovar("token-1", "token-2");
        sinPuertos.cerrar("token-1");
        sinPuertos.revocarUsuario(12L);

        assertThat(sinPuertos.expirarVencidas(AHORA)).isZero();
        verify(eventos, never()).publicarSesionCambiada(any());
        verify(jwt, never()).validarToken(any());
    }
}
