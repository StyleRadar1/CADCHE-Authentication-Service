package edu.dosw.styleradar.auth.domain.port.out;

import edu.dosw.styleradar.auth.application.service.GoogleIdentityClaims;

/**
 * Puerto de salida para verificación criptográfica de ID Tokens y
 * exchange de Authorization Codes contra Google Identity / OAuth2.
 */
public interface GoogleOAuthPort {

    GoogleIdentityClaims verificarIdToken(String idToken);

    String intercambiarCodigoPorIdToken(String code, String redirectUri);
}

