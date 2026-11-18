package edu.dosw.styleradar.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RabbitMQConfig: exchanges del broker del curso y enlace de la cola de estados")
class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    @DisplayName("Declara styleradar.users (topic) y styleradar.dlx (direct), ambos durables")
    void declaraExchanges() {
        assertThat(config.eventosExchange().getName()).isEqualTo("styleradar.users");
        assertThat(config.eventosExchange().getType()).isEqualTo("topic");
        assertThat(config.eventosExchange().isDurable()).isTrue();
        assertThat(config.mensajesMuertosExchange().getName()).isEqualTo("styleradar.dlx");
        assertThat(config.mensajesMuertosExchange().getType()).isEqualTo("direct");
        assertThat(config.mensajesMuertosExchange().isDurable()).isTrue();
    }

    @Test
    @DisplayName("La cola de auth se enlaza a styleradar.users con la routing key user.status.changed")
    void enlazaLaCola() {
        var enlace = config.usuarioEstadoBinding();

        assertThat(enlace.getExchange()).isEqualTo("styleradar.users");
        assertThat(enlace.getRoutingKey()).isEqualTo("user.status.changed");
        assertThat(enlace.getDestination()).isEqualTo("styleradar.auth.user-status-changed.q");
    }
}
