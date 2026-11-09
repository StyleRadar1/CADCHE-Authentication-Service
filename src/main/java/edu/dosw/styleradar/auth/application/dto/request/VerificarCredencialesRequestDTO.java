package edu.dosw.styleradar.auth.application.dto.request;

public record VerificarCredencialesRequestDTO(
        String email,
        String password
) {
}