package edu.dosw.styleradar.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class PerfilQaCorsTest {

    private static String origenes(String archivo) {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource(archivo));
        return yaml.getObject().getProperty("styleradar.cors.allowed-origins");
    }

    @Test
    @DisplayName("QA acepta el front local (localhost:5173) para conectar contra el Gateway QA")
    void qaPermiteFrontLocal() {
        assertThat(origenes("application-qa.yml")).contains("http://localhost:5173");
    }

    @Test
    @DisplayName("PROD no acepta orígenes localhost")
    void prodSinLocalhost() {
        assertThat(origenes("application-prod.yml")).doesNotContain("localhost");
    }
}
