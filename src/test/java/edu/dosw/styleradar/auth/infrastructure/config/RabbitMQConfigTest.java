package edu.dosw.styleradar.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RabbitMQConfig: exchanges del broker del curso y enlace de la cola de estados")
class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    @DisplayName("Declara styleradar.exchange (topic) y styleradar.dlx (direct), ambos durables")
    void declaraExchanges() {
        assertThat(config.eventosExchange().getName()).isEqualTo("styleradar.exchange");
        assertThat(config.eventosExchange().getType()).isEqualTo("topic");
        assertThat(config.eventosExchange().isDurable()).isTrue();
        assertThat(config.mensajesMuertosExchange().getName()).isEqualTo("styleradar.dlx");
        assertThat(config.mensajesMuertosExchange().getType()).isEqualTo("direct");
        assertThat(config.mensajesMuertosExchange().isDurable()).isTrue();
    }

    @Test
    @DisplayName("La cola de auth se enlaza a styleradar.exchange con la routing key de ms-usuarios")
    void enlazaLaCola() {
        var enlace = config.usuarioEstadoBinding();

        assertThat(enlace.getExchange()).isEqualTo("styleradar.exchange");
        assertThat(enlace.getRoutingKey()).isEqualTo("styleradar.usuarios.estado.cambiado");
        assertThat(enlace.getDestination()).isEqualTo("styleradar.auth.estado-cambiado.q");
    }
}
