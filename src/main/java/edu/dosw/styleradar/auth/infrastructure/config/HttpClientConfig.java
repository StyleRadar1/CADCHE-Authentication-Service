package edu.dosw.styleradar.auth.infrastructure.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;

/**
 * Cliente HTTP de salida (ms-usuarios y Google). Sin otra librería en el classpath, Spring Boot usa
 * {@code HttpURLConnection}, que no admite {@code PATCH}: la notificación de correo verificado a ms-usuarios
 * (contrato B2 / U3) fallaba siempre. El cliente de Java 21 admite todos los métodos y aplica tiempos de espera
 * para que una dependencia lenta responda {@code 503} en lugar de bloquear los hilos.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestClientCustomizer clienteHttpJdk(
            @Value("${styleradar.http.connect-timeout:PT5S}") Duration connectTimeout,
            @Value("${styleradar.http.read-timeout:PT10S}") Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(httpClient);
        fabrica.setReadTimeout(readTimeout);
        return builder -> builder.requestFactory(fabrica);
    }
}
