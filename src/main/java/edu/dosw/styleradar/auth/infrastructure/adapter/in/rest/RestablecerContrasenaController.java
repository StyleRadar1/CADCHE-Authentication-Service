package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.dosw.styleradar.auth.application.dto.request.RestablecerContrasenaRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.SolicitarRestablecimientoRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.RestablecimientoResponseDTO;
import edu.dosw.styleradar.auth.domain.model.SolicitudRestablecimiento;
import edu.dosw.styleradar.auth.domain.port.in.RestablecerContrasenaUseCase;
import edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.RestablecerContrasenaApi;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth/password")
public class RestablecerContrasenaController implements RestablecerContrasenaApi {

    static final String MENSAJE_SOLICITUD =
            "Si el correo está registrado, te enviamos un código para restablecer tu contraseña.";
    static final String MENSAJE_CAMBIADA = "Tu contraseña se actualizó. Inicia sesión con la nueva contraseña.";

    private final RestablecerContrasenaUseCase restablecerContrasenaUseCase;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Caso de uso administrado por Spring e inyectado por constructor.")
    public RestablecerContrasenaController(RestablecerContrasenaUseCase restablecerContrasenaUseCase) {
        this.restablecerContrasenaUseCase = restablecerContrasenaUseCase;
    }

    @Override
    @PostMapping("/forgot")
    public ResponseEntity<RestablecimientoResponseDTO> solicitar(
            @Valid @RequestBody SolicitarRestablecimientoRequestDTO request) {
        SolicitudRestablecimiento solicitud = restablecerContrasenaUseCase.solicitar(request.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new RestablecimientoResponseDTO(MENSAJE_SOLICITUD, solicitud.expiraEnSegundos()));
    }

    @Override
    @PostMapping("/reset")
    public ResponseEntity<RestablecimientoResponseDTO> restablecer(
            @Valid @RequestBody RestablecerContrasenaRequestDTO request) {
        restablecerContrasenaUseCase.restablecer(request.getEmail(), request.getOtpCode(), request.getNewPassword());
        return ResponseEntity.ok(new RestablecimientoResponseDTO(MENSAJE_CAMBIADA, null));
    }
}
