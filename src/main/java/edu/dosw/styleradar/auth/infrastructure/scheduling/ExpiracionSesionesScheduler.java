package edu.dosw.styleradar.auth.infrastructure.scheduling;

import java.time.Clock;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import edu.dosw.styleradar.auth.application.service.SesionesActivasService;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

/** Cada minuto cierra las sesiones cuyo token venció y publica su user.session.changed con delta -1. */
@Component
@ConditionalOnProperty(name = "styleradar.sessions.expiration-sweep.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class ExpiracionSesionesScheduler {

    private final SesionesActivasService sesiones;
    private final Clock clock;

    @SuppressFBWarnings(value = "EI_EXPOSE_REP2", justification = "Beans de Spring inyectados por constructor.")
    public ExpiracionSesionesScheduler(SesionesActivasService sesiones, Clock clock) {
        this.sesiones = sesiones;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "${styleradar.sessions.expiration-sweep.interval-ms:60000}",
            fixedDelayString = "${styleradar.sessions.expiration-sweep.interval-ms:60000}")
    public void barrer() {
        int cerradas = sesiones.expirarVencidas(Instant.now(clock));
        if (cerradas > 0) {
            log.info("Sesiones vencidas cerradas en el barrido: {}", cerradas);
        }
    }
}
