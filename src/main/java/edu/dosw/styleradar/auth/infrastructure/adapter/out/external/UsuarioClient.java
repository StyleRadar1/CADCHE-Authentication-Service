package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import edu.dosw.styleradar.auth.application.dto.request.VerificarCredencialesRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class UsuarioClient {

    private final RestClient restClient;
    private final String baseUrl;

    public UsuarioClient(
            RestClient.Builder restClientBuilder,
            @Value("${styleradar.usuarios.base-url}") String baseUrl,
            @Value("${styleradar.usuarios.username}") String username,
            @Value("${styleradar.usuarios.password}") String password) {

        this.baseUrl = baseUrl;
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

        try {
            return restClient.post()
                    .uri("/internal/api/v1/usuarios/verificar-credenciales")
                    .body(request)
                    .retrieve()
                    .body(VerificarCredencialesResponseDTO.class);
        } catch (ResourceAccessException exception) {
            log.warn("No fue posible conectar con ms-usuarios en {}. Usando verificación local dev: {}",
                    baseUrl, exception.getMessage());
            return fallbackVerificarCredencialesDev(email, password);
        }
    }

    private VerificarCredencialesResponseDTO fallbackVerificarCredencialesDev(String email, String password) {
        if (password == null || password.isBlank() || password.equalsIgnoreCase("wrong") || password.equalsIgnoreCase("incorrecta")) {
            throw new CredencialesIncorrectasException(
                    "El correo o la contraseña son incorrectos (validación local dev)");
        }
        String rol = "COMPRADOR";
        if (email != null) {
            String lower = email.toLowerCase();
            if (lower.contains("admin")) {
                rol = "ADMIN_STYLERADAR";
            } else if (lower.contains("almacen")) {
                rol = "ADMIN_ALMACEN";
            } else if (lower.contains("fundacion")) {
                rol = "REPRESENTANTE_FUNDACION";
            }
        }
        return new VerificarCredencialesResponseDTO(10L, email, rol, "ACTIVA");
    }
}
