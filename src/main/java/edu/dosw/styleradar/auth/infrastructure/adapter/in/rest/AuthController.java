package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.CuentaNoActivaException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApi;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
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
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        log.info("Intento de autenticación para usuario: {}", request.getEmail());

        VerificarCredencialesResponseDTO usuario;

        try {
            usuario = usuarioClient.verificarCredenciales(
                    request.getEmail(),
                    request.getPassword()
            );
        } catch (HttpClientErrorException.Unauthorized exception) {
            log.warn("Credenciales incorrectas para: {}", request.getEmail());
            throw new CredencialesIncorrectasException(
                    "El correo o la contraseña son incorrectos");
        } catch (HttpClientErrorException.Forbidden exception) {
            log.warn("Cuenta no activa para: {}", request.getEmail());
            throw new CuentaNoActivaException(
                    "La cuenta se encuentra inactiva o bloqueada");
        } catch (RestClientResponseException exception) {
            log.error("Fallo al conectar con ms-usuarios: {}", exception.getMessage());
            throw new IllegalStateException(
                    "No fue posible verificar las credenciales con el servicio de usuarios");
        }

        String codigo = otpService.generarOtp(
                request.getEmail(), usuario.id(), usuario.rol());

        try {
            emailService.enviarOtp(request.getEmail(), codigo);
        } catch (RuntimeException exception) {
            log.error("Fallo al enviar correo con OTP para {}: {}", request.getEmail(), exception.getMessage());
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
    @PostMapping({"/otp/verificar", "/verificar-otp"})
    public ResponseEntity<LoginResponseDTO> verificarOtp(
            @Valid @RequestBody VerificarOtpRequestDTO request) {

        String email = request.getEmail();
        log.info("Verificación de OTP solicitada para: {}", email);

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

        log.info("Acceso concedido y token JWT emitido para usuario ID {}", usuarioId);

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
    @PostMapping({"/otp/reenviar", "/reenviar-otp"})
    public ResponseEntity<OtpMensajeResponseDTO> reenviarOtp(
            @Valid @RequestBody ReenviarOtpRequestDTO request) {

        String email = request.getEmail();
        log.info("Solicitud de reenvío de OTP para correo: {}", email);

        String nuevoCodigo = otpService.reenviarOtp(email);
        emailService.enviarOtp(email, nuevoCodigo);

        return ResponseEntity.ok(new OtpMensajeResponseDTO(
                email,
                "Se envió un código de verificación de 6 dígitos a su correo registrado",
                300L,
                3
        ));
    }

    @Override
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponseDTO> refresh(
            @Valid @RequestBody RefreshTokenRequestDTO request) {

        String token = request.getRefreshToken();
        log.info("Solicitud de renovación de token de acceso JWT");

        if (token == null || !jwtService.validarToken(token) || otpService.esTokenInvalido(token)) {
            log.warn("Intento de renovación con token inválido, expirado o revocado");
            throw new CredencialesIncorrectasException(
                    "El token presentado es inválido, expiró o fue revocado");
        }

        Long usuarioId = jwtService.obtenerUsuarioId(token);
        Set<Rol> roles = jwtService.obtenerRoles(token);

        if (usuarioId == null || roles == null || roles.isEmpty()) {
            throw new CredencialesIncorrectasException(
                    "El token presentado no contiene información de identidad válida");
        }

        String nuevoToken = jwtService.generarToken(usuarioId, roles);
        log.info("Token JWT renovado exitosamente para usuario ID {}", usuarioId);

        return ResponseEntity.ok(new LoginResponseDTO(
                nuevoToken,
                "Bearer",
                86400L,
                usuarioId,
                roles
        ));
    }

    @Override
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        log.info("Solicitud de cierre de sesión");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7).trim();
            if (jwtService.validarToken(token)) {
                long tiempoRestante = jwtService.obtenerTiempoRestanteSegundos(token);
                if (tiempoRestante > 0) {
                    otpService.invalidarToken(token, tiempoRestante);
                    log.info("Token revocado en logout con TTL restante de {}s", tiempoRestante);
                }
            }
        }

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/registro")
    public ResponseEntity<UsuarioAuthResponseDTO> registrar(
            @Valid @RequestBody RegistroAuthRequestDTO request) {

        log.info("Registro de usuario solicitado para: {}", request.getEmail());

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
}
