package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import edu.dosw.styleradar.auth.application.dto.request.VerificarCredencialesRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UsuarioClient {

    private final RestClient restClient;

    public UsuarioClient(
            RestClient.Builder restClientBuilder,
            @Value("${styleradar.usuarios.base-url}") String baseUrl,
            @Value("${styleradar.usuarios.username}") String username,
            @Value("${styleradar.usuarios.password}") String password) {

        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeaders(headers ->
                        headers.setBasicAuth(username, password))
                .build();
    }

    public VerificarCredencialesResponseDTO verificarCredenciales(
            String email, String password) {

        VerificarCredencialesRequestDTO request =
                new VerificarCredencialesRequestDTO(email, password);

        return restClient.post()
                .uri("/internal/api/v1/usuarios/verificar-credenciales")
                .body(request)
                .retrieve()
                .body(VerificarCredencialesResponseDTO.class);
    }
}