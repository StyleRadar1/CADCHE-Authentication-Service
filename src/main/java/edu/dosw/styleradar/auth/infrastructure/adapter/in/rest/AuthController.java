package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientResponseException;

import edu.dosw.styleradar.auth.application.dto.request.LoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.ReenviarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RefreshTokenRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RegistroAuthRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.VerificarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.OtpMensajeResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.UsuarioAuthResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.VerificarCredencialesResponseDTO;
import edu.dosw.styleradar.auth.application.service.EmailService;
import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.application.service.OtpService;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApi;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApi {

    private final UsuarioClient usuarioClient;
    private final OtpService otpService;
    private final EmailService emailService;
    private final JwtService jwtService;

    public AuthController(
            UsuarioClient usuarioClient,
            OtpService otpService,
            EmailService emailService,
            JwtService jwtService) {
        this.usuarioClient = usuarioClient;
        this.otpService = otpService;
        this.emailService = emailService;
        this.jwtService = jwtService;
    }

    @Override
    public ResponseEntity<LoginResponseDTO> login(LoginRequestDTO request) {

        VerificarCredencialesResponseDTO usuario;

        try {
            usuario = usuarioClient.verificarCredenciales(
                    request.getEmail(),
                    request.getPassword()
            );
        } catch (HttpClientErrorException.Unauthorized exception) {
            throw new CredencialesIncorrectasException(
                    "El correo o la contraseña son incorrectos");
        } catch (HttpClientErrorException.Forbidden exception) {
            throw new CuentaNoActivaException(
                    "La cuenta se encuentra inactiva o bloqueada");
        } catch (RestClientResponseException exception) {
            throw new IllegalStateException(
                    "No fue posible verificar las credenciales con el servicio de usuarios");
        }

        String codigo = otpService.generarOtp(
                request.getEmail(), usuario.id(), usuario.rol());

        try {
            emailService.enviarOtp(request.getEmail(), codigo);
        } catch (RuntimeException exception) {
            otpService.eliminarOtp(request.getEmail());
            throw exception;
        }

        Rol rol = Rol.valueOf(usuario.rol());

        return ResponseEntity.ok(new LoginResponseDTO(
                null,
                "Bearer",
                86400L,
                usuario.id(),
                Set.of(rol),
                true,
                300,
                3
        ));
    }

    @Override
    public ResponseEntity<LoginResponseDTO> verificarOtp(
            VerificarOtpRequestDTO request) {

        String email = request.getEmail();

        Long usuarioId = otpService.obtenerUsuarioId(email);
        String nombreRol = otpService.obtenerRol(email);

        otpService.verificarOtp(email, request.getCodigoOtp());

        if (usuarioId == null || nombreRol == null) {
            otpService.eliminarOtp(email);
            throw new IllegalStateException(
                    "No se encontraron los datos de la sesión");
        }

        Rol rol = Rol.valueOf(nombreRol);
        Set<Rol> roles = Set.of(rol);

        String token = jwtService.generarToken(usuarioId, roles);

        otpService.eliminarOtp(email);

        return ResponseEntity.ok(new LoginResponseDTO(
                token,
                "Bearer",
                86400L,
                usuarioId,
                roles,
                false,
                null,
                0
        ));
    }

    @Override
    public ResponseEntity<UsuarioAuthResponseDTO> registrar(
            RegistroAuthRequestDTO request) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                UsuarioAuthResponseDTO.builder()
                        .id(12L)
                        .nombre(request.getNombre())
                        .email(request.getEmail())
                        .telefono(request.getTelefono())
                        .fechaRegistro(Instant.now())
                        .roles(Set.of(Rol.COMPRADOR))
                        .build()
        );
    }

    @Override
    public ResponseEntity<OtpMensajeResponseDTO> reenviarOtp(
            ReenviarOtpRequestDTO request) {

        return ResponseEntity.ok(new OtpMensajeResponseDTO(
                request.getEmail(),
                "Se envió un código de verificación de 6 dígitos a su correo registrado",
                300L,
                3
        ));
    }

    @Override
    public ResponseEntity<LoginResponseDTO> refresh(
            RefreshTokenRequestDTO request) {

        return ResponseEntity.ok(new LoginResponseDTO(
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiJ9.nuevaFirma",
                "Bearer",
                86400L,
                12L,
                Set.of(Rol.COMPRADOR)
        ));
    }

    @Override
    public ResponseEntity<Void> logout(String authorizationHeader) {
        return ResponseEntity.noContent().build();
    }
}
