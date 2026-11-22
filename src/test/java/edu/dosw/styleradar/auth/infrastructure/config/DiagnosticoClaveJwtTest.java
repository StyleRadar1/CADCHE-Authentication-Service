package edu.dosw.styleradar.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class DiagnosticoClaveJwtTest {

    private static final String LLAVE = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

    @Test
    @DisplayName("Indica que la variable de entorno gana sobre el YAML y calcula huellas sin exponer la llave")
    void fuenteYHuellas() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("yaml-qa", Map.of(DiagnosticoClaveJwt.PROPIEDAD, "otra")));
        env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("entorno-azure",
                Map.of("STYLERADAR_SECURITY_JWT_SECRET_BASE64", LLAVE)));
        DiagnosticoClaveJwt diagnostico = new DiagnosticoClaveJwt(env, Map.of("JWT_SECRET_BASE64", "@@@")::get);

        assertThat(diagnostico.fuenteGanadora()).isEqualTo("entorno-azure");
        assertThat(DiagnosticoClaveJwt.huella(LLAVE)).matches("[0-9A-F]{12}");
        assertThat(DiagnosticoClaveJwt.huella(null)).isEqualTo("no definida");
        assertThat(DiagnosticoClaveJwt.huella("@@@")).isEqualTo("no es Base64 válido");
        assertThatCode(diagnostico::registrar).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sin la propiedad en ninguna fuente responde 'ninguna'")
    void sinFuente() {
        assertThat(new DiagnosticoClaveJwt(new StandardEnvironment(), k -> null).fuenteGanadora()).isEqualTo("ninguna");
    }
}
