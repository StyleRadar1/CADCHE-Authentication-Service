package edu.dosw.styleradar.auth.application.dto.response;

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
}
