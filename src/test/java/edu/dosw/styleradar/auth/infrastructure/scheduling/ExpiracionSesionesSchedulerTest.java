package edu.dosw.styleradar.auth.infrastructure.scheduling;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import edu.dosw.styleradar.auth.application.service.SesionesActivasService;

@DisplayName("Barrido periódico de sesiones vencidas")
class ExpiracionSesionesSchedulerTest {

    @Test
    @DisplayName("Cada ejecución revisa los tokens vencidos a la hora actual")
    void barre() {
        Instant ahora = Instant.parse("2026-11-25T15:04:05Z");
        SesionesActivasService sesiones = mock(SesionesActivasService.class);
        when(sesiones.expirarVencidas(ahora)).thenReturn(2).thenReturn(0);
        ExpiracionSesionesScheduler scheduler = new ExpiracionSesionesScheduler(sesiones, Clock.fixed(ahora, ZoneOffset.UTC));

        scheduler.barrer();
        scheduler.barrer();

        verify(sesiones, times(2)).expirarVencidas(ahora);
    }
}
