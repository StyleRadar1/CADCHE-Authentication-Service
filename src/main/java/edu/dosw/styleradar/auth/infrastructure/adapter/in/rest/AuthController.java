package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.dosw.styleradar.auth.application.dto.request.LoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.ReenviarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RefreshTokenRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.VerificarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.OtpMensajeResponseDTO;
import edu.dosw.styleradar.auth.application.mapper.out.AuthResponseMapper;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.port.in.CerrarSesionUseCase;
import edu.dosw.styleradar.auth.domain.port.in.IniciarSesionUseCase;
import edu.dosw.styleradar.auth.domain.port.in.ReenviarOtpUseCase;
import edu.dosw.styleradar.auth.domain.port.in.RenovarTokenUseCase;
import edu.dosw.styleradar.auth.domain.port.in.VerificarOtpUseCase;
import edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApi;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.validation.Valid;

/**
 * Adaptador REST de entrada. Solo conoce los puertos de entrada (casos de uso) y el mapper de salida.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApi {

    private static final String BEARER_PREFIX = "Bearer ";

    private final IniciarSesionUseCase iniciarSesionUseCase;
    private final VerificarOtpUseCase verificarOtpUseCase;
    private final ReenviarOtpUseCase reenviarOtpUseCase;
    private final RenovarTokenUseCase renovarTokenUseCase;
    private final CerrarSesionUseCase cerrarSesionUseCase;
    private final AuthResponseMapper mapper;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Casos de uso administrados por Spring e inyectados por constructor.")
    @SuppressWarnings("java:S107")
    public AuthController(
            IniciarSesionUseCase iniciarSesionUseCase,
            VerificarOtpUseCase verificarOtpUseCase,
            ReenviarOtpUseCase reenviarOtpUseCase,
            RenovarTokenUseCase renovarTokenUseCase,
            CerrarSesionUseCase cerrarSesionUseCase,
            AuthResponseMapper mapper) {
        this.iniciarSesionUseCase = iniciarSesionUseCase;
        this.verificarOtpUseCase = verificarOtpUseCase;
        this.reenviarOtpUseCase = reenviarOtpUseCase;
        this.renovarTokenUseCase = renovarTokenUseCase;
        this.cerrarSesionUseCase = cerrarSesionUseCase;
        this.mapper = mapper;
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(mapper.toLoginResponse(
                iniciarSesionUseCase.iniciarSesion(request.getEmail(), request.getPassword())));
    }

    @Override
    @PostMapping("/otp/verify")
    public ResponseEntity<LoginResponseDTO> verificarOtp(@Valid @RequestBody VerificarOtpRequestDTO request) {
        return ResponseEntity.ok(mapper.toLoginResponse(
                verificarOtpUseCase.verificar(request.getEmail(), request.getOtpCode())));
    }

    @Override
    @PostMapping("/otp/resend")
    public ResponseEntity<OtpMensajeResponseDTO> reenviarOtp(@Valid @RequestBody ReenviarOtpRequestDTO request) {
        return ResponseEntity.ok(mapper.toOtpMensajeResponse(reenviarOtpUseCase.reenviar(request.getEmail())));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(@Valid @RequestBody RefreshTokenRequestDTO request) {
        return ResponseEntity.ok(mapper.toLoginResponse(renovarTokenUseCase.renovar(request.getRefreshToken())));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw CredencialesIncorrectasException.tokenInvalido("No autorizado: se requiere un token Bearer válido");
        }
        try {
            cerrarSesionUseCase.cerrarSesion(authorizationHeader.substring(BEARER_PREFIX.length()).trim());
        } finally {
            SecurityContextHolder.clearContext();
        }
        return ResponseEntity.noContent().build();
    }
}
