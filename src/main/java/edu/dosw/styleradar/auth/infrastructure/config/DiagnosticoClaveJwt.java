package edu.dosw.styleradar.auth.infrastructure.config;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.function.UnaryOperator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

import edu.dosw.styleradar.auth.application.service.impl.JwtServiceImpl;
import lombok.extern.slf4j.Slf4j;

/**
 * Al arrancar, escribe en el log de dónde sale la llave JWT: perfiles activos, la fuente de configuración que
 * gana para {@code styleradar.security.jwt.secret-base64} y la huella (12 hex del SHA-256 de la llave
 * decodificada) de cada variable de entorno candidata. Nunca escribe la llave.
 */
@Component
@Slf4j
public class DiagnosticoClaveJwt {

    static final String PROPIEDAD = "styleradar.security.jwt.secret-base64";
    static final List<String> VARIABLES =
            List.of("JWT_SECRET_QA_BASE64", "JWT_SECRET_BASE64", "STYLERADAR_SECURITY_JWT_SECRET_BASE64");

    private final ConfigurableEnvironment environment;
    private final UnaryOperator<String> variablesDeEntorno;

    @Autowired
    public DiagnosticoClaveJwt(ConfigurableEnvironment environment) {
        this(environment, System::getenv);
    }

    DiagnosticoClaveJwt(ConfigurableEnvironment environment, UnaryOperator<String> variablesDeEntorno) {
        this.environment = environment;
        this.variablesDeEntorno = variablesDeEntorno;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registrar() {
        log.info("Diagnóstico JWT: perfiles activos = {}", Arrays.toString(environment.getActiveProfiles()));
        log.info("Diagnóstico JWT: {} sale de la fuente [{}] con huella {}", PROPIEDAD, fuenteGanadora(),
                huella(environment.getProperty(PROPIEDAD)));
        for (String variable : VARIABLES) {
            log.info("Diagnóstico JWT: variable de entorno {} -> {}", variable, huella(variablesDeEntorno.apply(variable)));
        }
    }

    String fuenteGanadora() {
        for (PropertySource<?> fuente : environment.getPropertySources()) {
            boolean laTiene = fuente instanceof EnumerablePropertySource<?> enumerable
                    ? enumerable.containsProperty(PROPIEDAD)
                    : fuente.getProperty(PROPIEDAD) != null;
            if (laTiene && !"configurationProperties".equals(fuente.getName())) {
                return fuente.getName();
            }
        }
        return "ninguna";
    }

    static String huella(String valor) {
        if (valor == null || valor.isBlank()) {
            return "no definida";
        }
        try {
            return JwtServiceImpl.huellaCorta(Base64.getDecoder().decode(valor.trim()));
        } catch (IllegalArgumentException ex) {
            return "no es Base64 válido";
        }
    }
}
