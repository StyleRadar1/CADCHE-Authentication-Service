package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import java.time.Instant;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
import edu.dosw.styleradar.auth.domain.exception.OtpExpiradoException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.Rol;
import edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.AuthApi;
import edu.dosw.styleradar.auth.infrastructure.adapter.out.external.UsuarioClient;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
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
    private final boolean registrationStubEnabled;
    private final Environment environment;

    public AuthController(
            UsuarioClient usuarioClient,
            OtpService otpService,
            EmailService emailService,
            JwtService jwtService) {
        this(usuarioClient, otpService, emailService, jwtService, false, null);
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Dependencias administradas por Spring e inyectadas por constructor.")
    @Autowired
    public AuthController(
            UsuarioClient usuarioClient,
            OtpService otpService,
            EmailService emailService,
            JwtService jwtService,
            @Value("${styleradar.security.registration.stub-enabled:false}") boolean registrationStubEnabled,
            Environment environment) {
        this.usuarioClient = usuarioClient;
        this.otpService = otpService;
        this.emailService = emailService;
        this.jwtService = jwtService;
        this.registrationStubEnabled = registrationStubEnabled;
        this.environment = environment;
    }

    @Override
    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody LoginRequestDTO request) {
        String email = SecuritySanitizer.normalizeEmail(request.getEmail());
        String maskedEmail = SecuritySanitizer.maskEmail(email);
        log.info("Intento de autenticación para usuario: {}", maskedEmail);

        VerificarCredencialesResponseDTO usuario;

        try {
            usuario = usuarioClient.verificarCredenciales(email, request.getPassword());
        } catch (HttpClientErrorException.Unauthorized exception) {
            log.warn("Credenciales incorrectas para: {}", maskedEmail);
            throw new CredencialesIncorrectasException(
                    "El correo o la contraseña son incorrectos");
        } catch (HttpClientErrorException.Forbidden exception) {
            log.warn("Cuenta no activa para: {}", maskedEmail);
            throw new CuentaNoActivaException(
                    "La cuenta se encuentra inactiva o bloqueada");
        } catch (RestClientResponseException exception) {
            log.error("Fallo al conectar con ms-usuarios (HTTP {}): {}",
                    exception.getStatusCode().value(), SecuritySanitizer.sanitizeForLog(exception.getMessage()));
            throw new ServicioExternoNoDisponibleException(
                    "No fue posible verificar las credenciales con el servicio de usuarios", exception);
        }

        if (usuario == null || usuario.id() == null) {
            throw new ServicioExternoNoDisponibleException(
                    "Respuesta inválida del servicio de usuarios al verificar credenciales");
        }

        Rol rol = parsearRolSeguro(usuario.rol());
        String codigo = otpService.generarOtp(email, usuario.id(), rol.name());
        enviarOtpConRollback(email, maskedEmail, codigo);

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
    @PostMapping("/otp/verificar")
    public ResponseEntity<LoginResponseDTO> verificarOtp(
            @Valid @RequestBody VerificarOtpRequestDTO request) {

        String email = SecuritySanitizer.normalizeEmail(request.getEmail());
        String maskedEmail = SecuritySanitizer.maskEmail(email);
        log.info("Verificación de OTP solicitada para: {}", maskedEmail);

        Long usuarioId = otpService.obtenerUsuarioId(email);
        String nombreRol = otpService.obtenerRol(email);

        otpService.verificarOtp(email, request.getCodigoOtp());

        if (usuarioId == null || nombreRol == null) {
            otpService.eliminarOtp(email);
            throw new OtpExpiradoException(
                    "No se encontraron los datos de la sesión OTP. Inicie sesión nuevamente.");
        }

        Rol rol = parsearRolSeguro(nombreRol);
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
    @PostMapping("/otp/reenviar")
    public ResponseEntity<OtpMensajeResponseDTO> reenviarOtp(
            @Valid @RequestBody ReenviarOtpRequestDTO request) {

        String email = SecuritySanitizer.normalizeEmail(request.getEmail());
        String maskedEmail = SecuritySanitizer.maskEmail(email);
        log.info("Solicitud de reenvío de OTP para correo: {}", maskedEmail);

        String nuevoCodigo = otpService.reenviarOtp(email);
        enviarOtpConRollback(email, maskedEmail, nuevoCodigo);

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

        String token = request.getRefreshToken() != null ? request.getRefreshToken().trim() : null;
        log.info("Solicitud de renovación de token de acceso JWT");

        if (token == null || token.isBlank() || otpService.esTokenInvalido(token)) {
            log.warn("Intento de renovación con token inválido, expirado o revocado");
            throw new CredencialesIncorrectasException(
                    "El token presentado es inválido, expiró o fue revocado");
        }

        Claims claims;
        try {
            claims = jwtService.validarToken(token);
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Intento de renovación con token JWT inválido o expirado");
            throw new CredencialesIncorrectasException(
                    "El token presentado es inválido, expiró o fue revocado");
        }

        Long usuarioId = jwtService.obtenerUsuarioId(token);
        Set<Rol> roles = jwtService.obtenerRoles(token);

        if (usuarioId == null || roles == null || roles.isEmpty()) {
            throw new CredencialesIncorrectasException(
                    "El token presentado no contiene información de identidad válida");
        }

        long issuedAtMillis = claims != null && claims.getIssuedAt() != null
                ? claims.getIssuedAt().getTime()
                : 0L;
        if (otpService.estaUsuarioRevocado(usuarioId, issuedAtMillis)) {
            log.warn("Renovación rechazada para usuario ID {} por revocación global de cuenta", usuarioId);
            throw new CredencialesIncorrectasException(
                    "El token presentado es inválido, expiró o fue revocado");
        }

        long restante = jwtService.obtenerTiempoRestanteSegundos(token);
        if (restante > 0) {
            otpService.invalidarToken(token, restante);
        }

        String nuevoToken = jwtService.generarToken(usuarioId, roles);
        log.info("Token JWT renovado exitosamente con rotación para usuario ID {}", usuarioId);

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

        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new CredencialesIncorrectasException(
                    "No autorizado: se requiere un token Bearer válido");
        }

        String token = authorizationHeader.substring(7).trim();
        if (token.isEmpty()) {
            throw new CredencialesIncorrectasException(
                    "No autorizado: token vacío o malformado");
        }

        try {
            jwtService.validarToken(token);

            long tiempoRestante = jwtService.obtenerTiempoRestanteSegundos(token);
            if (tiempoRestante > 0) {
                otpService.invalidarToken(token, tiempoRestante);
                log.info("Token revocado en logout con TTL restante de {}s", tiempoRestante);
            }
        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Se intentó cerrar sesión con un token inválido o expirado");
            throw new CredencialesIncorrectasException("El token presentado es inválido o expirado");
        } finally {
            SecurityContextHolder.clearContext();
        }

        return ResponseEntity.noContent().build();
    }

    @Override
    @PostMapping("/registro")
    public ResponseEntity<UsuarioAuthResponseDTO> registrar(
            @Valid @RequestBody RegistroAuthRequestDTO request) {

        String normalizedEmail = SecuritySanitizer.normalizeEmail(request.getEmail());
        String maskedEmail = SecuritySanitizer.maskEmail(normalizedEmail);

        if (!isRegistrationStubAllowed()) {
            log.warn("Intento de registro en modo fail-closed bloqueado para {}", maskedEmail);
            throw new ServicioExternoNoDisponibleException(
                    "El registro directo no está disponible en este entorno hasta completar la sincronización con ms-usuarios");
        }

        log.info("Registro de usuario solicitado para: {}", maskedEmail);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                UsuarioAuthResponseDTO.builder()
                        .id(12L)
                        .nombre(request.getNombre().trim())
                        .email(normalizedEmail)
                        .telefono(request.getTelefono())
                        .fechaRegistro(Instant.now())
                        .roles(Set.of(Rol.COMPRADOR))
                        .build()
        );
    }

    private void enviarOtpConRollback(String email, String maskedEmail, String codigo) {
        try {
            emailService.enviarOtp(email, codigo);
        } catch (RuntimeException exception) {
            log.error("Fallo al enviar correo con OTP para {}: {}",
                    maskedEmail, SecuritySanitizer.sanitizeForLog(exception.getMessage()));
            otpService.eliminarOtp(email);
            throw exception;
        }
    }

    private Rol parsearRolSeguro(String rolNombre) {
        if (rolNombre == null || rolNombre.isBlank()) {
            throw new CredencialesIncorrectasException("Rol de usuario no válido en la respuesta de identidad");
        }
        try {
            return Rol.valueOf(rolNombre);
        } catch (IllegalArgumentException ex) {
            throw new CredencialesIncorrectasException("Rol de usuario no válido en la respuesta de identidad");
        }
    }

    private boolean isRegistrationStubAllowed() {
        if (!registrationStubEnabled) {
            return false;
        }
        if (environment == null) {
            return true;
        }
        return environment.acceptsProfiles(Profiles.of("dev", "test"))
                && !environment.acceptsProfiles(Profiles.of("prod", "qa"));
    }
}
