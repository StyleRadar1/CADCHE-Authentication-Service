package edu.dosw.styleradar.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class PerfilProdConfigTest {

    private static Properties prod() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        return yaml.getObject();
    }

    @Test
    @DisplayName("PROD nunca apunta por defecto al broker RabbitMQ de QA")
    void prodSinBrokerDeQa() {
        assertThat(prod().getProperty("spring.rabbitmq.host")).doesNotContainIgnoringCase("qa");
    }

    @Test
    @DisplayName("PROD exige JWT_SECRET_BASE64 sin valor por defecto")
    void prodExigeSecretoJwt() {
        assertThat(prod().getProperty("styleradar.security.jwt.secret-base64")).isEqualTo("${JWT_SECRET_BASE64}");
    }
}
