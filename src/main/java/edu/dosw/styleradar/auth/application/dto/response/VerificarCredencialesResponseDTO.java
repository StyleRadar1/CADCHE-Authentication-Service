package edu.dosw.styleradar.auth.application.dto.response;

public record VerificarCredencialesResponseDTO(
        Long id,
        String email,
        String rol,
        String estado
) {
}