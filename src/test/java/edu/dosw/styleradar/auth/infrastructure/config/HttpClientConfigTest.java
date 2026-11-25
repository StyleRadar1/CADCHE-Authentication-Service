package edu.dosw.styleradar.auth.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.sun.net.httpserver.HttpServer;

/**
 * Contrato B2 / U3: ms-auth avisa a ms-usuarios con PATCH. Con HttpURLConnection (cliente por defecto de Spring
 * Boot sin otra librería HTTP) ese método fallaba siempre con "Invalid HTTP method: PATCH".
 */
@DisplayName("HttpClientConfig: el cliente HTTP de salida admite PATCH")
class HttpClientConfigTest {

    private HttpServer servidor;
    private final AtomicReference<String> metodoRecibido = new AtomicReference<>();

    @BeforeEach
    void iniciar() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/api/v1/internal/users/13/email-verification", intercambio -> {
            metodoRecibido.set(intercambio.getRequestMethod());
            intercambio.sendResponseHeaders(204, -1);
            intercambio.close();
        });
        servidor.start();
    }

    @AfterEach
    void detener() {
        servidor.stop(0);
    }

    @Test
    @DisplayName("Envía PATCH a ms-usuarios y recibe 204")
    void enviaPatch() {
        RestClient.Builder builder = RestClient.builder();
        new HttpClientConfig().clienteHttpJdk(Duration.ofSeconds(2), Duration.ofSeconds(2)).customize(builder);
        RestClient cliente = builder.baseUrl("http://127.0.0.1:" + servidor.getAddress().getPort()).build();

        var respuesta = cliente.patch()
                .uri("/api/v1/internal/users/{id}/email-verification", 13)
                .retrieve()
                .toBodilessEntity();

        assertThat(respuesta.getStatusCode().value()).isEqualTo(204);
        assertThat(metodoRecibido.get()).isEqualTo("PATCH");
    }
}
