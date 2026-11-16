package edu.dosw.styleradar.auth.application.service;

import edu.dosw.styleradar.auth.application.dto.response.GoogleAuthUrlResponseDTO;
import edu.dosw.styleradar.auth.application.dto.response.LoginResponseDTO;

/**
 * Servicio de aplicación para autenticación federada con Google OAuth2 / OpenID Connect.
 */
public interface GoogleOAuth2Service {

    GoogleAuthUrlResponseDTO generarUrlAutorizacion(String redirectUri);

    LoginResponseDTO autenticarConIdToken(String idToken);

    LoginResponseDTO autenticarConCodigo(String code, String state, String redirectUri);
}

