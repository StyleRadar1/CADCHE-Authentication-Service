package edu.dosw.styleradar.auth.infrastructure.adapter.in.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import edu.dosw.styleradar.auth.application.dto.request.GoogleCodeLoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.request.GoogleLoginRequestDTO;
import edu.dosw.styleradar.auth.application.dto.response.GoogleAuthUrlResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;
import edu.dosw.styleradar.auth.application.service.GoogleOAuth2Service;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.infrastructure.adapter.in.rest.doc.GoogleOAuth2Api;
import edu.dosw.styleradar.auth.infrastructure.security.SecuritySanitizer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/auth/oauth2/google")
@Slf4j
public class GoogleOAuth2Controller implements GoogleOAuth2Api {

    private final GoogleOAuth2Service googleOAuth2Service;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification = "Servicio de aplicación administrado por Spring e inyectado por constructor.")
    public GoogleOAuth2Controller(GoogleOAuth2Service googleOAuth2Service) {
        this.googleOAuth2Service = googleOAuth2Service;
    }

    @Override
    public ResponseEntity<LoginResponseDTO> autenticarConGoogle(GoogleLoginRequestDTO request) {
        return ResponseEntity.ok(googleOAuth2Service.autenticarConIdToken(request.getIdToken()));
    }

    @Override
    public ResponseEntity<GoogleAuthUrlResponseDTO> obtenerUrlAutorizacion(String redirectUri) {
        return ResponseEntity.ok(googleOAuth2Service.generarUrlAutorizacion(redirectUri));
    }

    @Override
    public ResponseEntity<LoginResponseDTO> callbackGoogle(
            String code, String state, String redirectUri, String error) {
        if (error != null && !error.isBlank()) {
            log.warn("Callback de Google OAuth2 recibió error={}",
                    SecuritySanitizer.sanitizeForLog(error));
            throw new CredencialesIncorrectasException(
                    "La autenticación con Google fue denegada o cancelada por el usuario");
        }
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new IllegalArgumentException(
                    "Los parámetros code y state son obligatorios en el callback de Google OAuth2");
        }
        return ResponseEntity.ok(googleOAuth2Service.autenticarConCodigo(code, state, redirectUri));
    }

    @Override
    public ResponseEntity<LoginResponseDTO> autenticarConCodigo(GoogleCodeLoginRequestDTO request) {
        return ResponseEntity.ok(googleOAuth2Service.autenticarConCodigo(
                request.getCode(),
                request.getState(),
                request.getRedirectUri()));
    }
}

