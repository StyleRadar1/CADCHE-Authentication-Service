package edu.dosw.styleradar.auth.application.dto.request;

public record OAuthUsuarioRequestDTO(
        String email,
        String nombre,
        String proveedor,
        String proveedorId
) {
}

