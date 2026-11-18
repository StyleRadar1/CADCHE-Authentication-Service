package edu.dosw.styleradar.auth.application.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta de ms-usuarios al verificar credenciales. Acepta los nombres del análisis v2 de ms-usuarios
 * ({@code role}/{@code roles}, {@code status}/{@code accountStatus}, {@code emailVerified}) y los anteriores en
 * español ({@code rol}, {@code estado}, {@code correoVerificado}) mientras ms-usuarios migra.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VerificarCredencialesResponseDTO(
        Long id,
        String email,
        String rol,
        String estado,
        Boolean correoVerificado
) {
    public VerificarCredencialesResponseDTO(Long id, String email, String rol, String estado) {
        this(id, email, rol, estado, Boolean.FALSE);
    }

    @JsonCreator
    public static VerificarCredencialesResponseDTO desdeJson(
            @JsonProperty("id") @JsonAlias("userId") Long id,
            @JsonProperty("email") String email,
            @JsonProperty("role") @JsonAlias("rol") String rol,
            @JsonProperty("roles") List<String> roles,
            @JsonProperty("status") @JsonAlias({"estado", "accountStatus"}) String estado,
            @JsonProperty("emailVerified") @JsonAlias("correoVerificado") Boolean correoVerificado) {
        String rolFinal = rol != null ? rol : (roles == null || roles.isEmpty() ? null : roles.get(0));
        return new VerificarCredencialesResponseDTO(id, email, rolFinal, estado,
                correoVerificado == null ? Boolean.FALSE : correoVerificado);
    }
}
