package edu.dosw.styleradar.auth.application.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Cuerpo que ms-auth envía a ms-usuarios para crear o vincular una cuenta de Google (nombres en inglés). */
public record OAuthUsuarioRequestDTO(
        @JsonProperty("email") String email,
        @JsonProperty("name") String nombre,
        @JsonProperty("provider") String proveedor,
        @JsonProperty("providerId") String proveedorId
) {
}
