package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.dosw.styleradar.auth.application.dto.request.LoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.ReenviarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RefreshTokenRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.RegistroAuthRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.VerificarOtpRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.OtpMensajeResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.UsuarioAuthResponseDTO;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApi;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController implements AuthApi {

    @Override
    public ResponseEntity<LoginResponseDTO> login(LoginRequestDTO request) {
        return ResponseEntity.ok(new LoginResponseDTO(
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsInJvbGVzIjpbIkNPTVBSQURPUiJdfQ.firma",
                "Bearer",
                86400L,
                12L,
                Set.of(Rol.COMPRADOR)));
    }

    @Override
    public ResponseEntity<UsuarioAuthResponseDTO> registrar(RegistroAuthRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(UsuarioAuthResponseDTO.builder()
                .id(12L)
                .nombre(request.getNombre())
                .email(request.getEmail())
                .telefono(request.getTelefono())
                .fechaRegistro(Instant.now())
                .roles(Set.of(Rol.COMPRADOR))
                .build());
    }

    @Override
    public ResponseEntity<LoginResponseDTO> verificarOtp(VerificarOtpRequestDTO request) {
        return ResponseEntity.ok(new LoginResponseDTO(
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsInJvbGVzIjpbIkNPTVBSQURPUiJdfQ.firma",
                "Bearer",
                86400L,
                12L,
                Set.of(Rol.COMPRADOR)));
    }

    @Override
    public ResponseEntity<OtpMensajeResponseDTO> reenviarOtp(ReenviarOtpRequestDTO request) {
        return ResponseEntity.ok(new OtpMensajeResponseDTO(
                request.getEmail(),
                "Se envió un código de verificación de 6 dígitos a su correo registrado",
                300L,
                3));
    }

    @Override
    public ResponseEntity<LoginResponseDTO> refresh(RefreshTokenRequestDTO request) {
        return ResponseEntity.ok(new LoginResponseDTO(
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMiIsInJvbGVzIjpbIkNPTVBSQURPUiJdfQ.nuevaFirma",
                "Bearer",
                86400L,
                12L,
                Set.of(Rol.COMPRADOR)));
    }

    @Override
    public ResponseEntity<Void> logout(String authorizationHeader) {
        return ResponseEntity.noContent().build();
    }
}

