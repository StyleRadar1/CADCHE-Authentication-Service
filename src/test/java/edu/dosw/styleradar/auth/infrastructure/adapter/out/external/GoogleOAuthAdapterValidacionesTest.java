package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import edu.dosw.styleradar.auth.domain.model.GoogleIdentityClaims;

/**
 * Validaciones de entrada y de claims de Google que no dependen de la firma: el decodificador se
 * reemplaza por uno que devuelve los claims de cada caso.
 */
@DisplayName("GoogleOAuthAdapter: validaciones de entrada y de claims")
class GoogleOAuthAdapterValidacionesTest {

    private static final String CLIENT_ID = "styleradar-web-client.apps.googleusercontent.com";
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    private static GoogleOAuthAdapter adaptador(String clientIds, String secreto, JwtDecoder decoder) {
        return new GoogleOAuthAdapter(RestClient.builder(), clientIds, secreto, TOKEN_URL, JWKS_URL,
                new MockEnvironment(), decoder);
    }

    private static JwtDecoder decoderCon(Consumer<Map<String, Object>> claims) {
        return token -> {
            Map<String, Object> mapa = new HashMap<>();
            mapa.put("iss", "https://accounts.google.com");
            claims.accept(mapa);
            return new Jwt(token, Instant.now(), Instant.now().plusSeconds(300), Map.of("alg", "RS256"), mapa);
        };
    }

    @ParameterizedTest(name = "sub=[{0}] email=[{1}]")
    @CsvSource(value = {"NULL,ana@gmail.com", "' ',ana@gmail.com", "g-1,NULL", "g-1,' '"}, nullValues = "NULL")
    void sinSubjectOCorreoSeRechaza(String sub, String email) {
        GoogleOAuthAdapter adapter = adaptador(CLIENT_ID, "s", decoderCon(c -> {
            if (sub != null) {
                c.put("sub", sub);
            }
            if (email != null) {
                c.put("email", email);
            }
            c.put("email_verified", true);
        }));
        assertThrows(CredencialesIncorrectasException.class, () -> adapter.verificarIdToken("token"));
    }

    @ParameterizedTest(name = "email_verified={0}")
    @CsvSource({"false", "no", "42"})
    void correoNoVerificadoSeRechaza(String valor) {
        Object claim = "42".equals(valor) ? (Object) 42 : (Object) valor;
        GoogleOAuthAdapter adapter = adaptador(CLIENT_ID, "s", decoderCon(c -> {
            c.put("sub", "g-1");
            c.put("email", "ana@gmail.com");
            c.put("email_verified", claim);
        }));
        assertThrows(CredencialesIncorrectasException.class, () -> adapter.verificarIdToken("token"));
    }

    @Test
    void correoVerificadoComoTextoYSinNombreSeAcepta() {
        GoogleOAuthAdapter adapter = adaptador(CLIENT_ID + ", otro-cliente ,", "s", decoderCon(c -> {
            c.put("sub", " g-1 ");
            c.put("email", "Ana@Gmail.com");
            c.put("email_verified", " TRUE ");
        }));
        GoogleIdentityClaims claims = adapter.verificarIdToken(" token ");
        assertEquals("g-1", claims.subject());
        assertEquals("ana@gmail.com", claims.email());
        assertEquals("", claims.name());
    }

    @Test
    void idTokenVacioOSinClienteConfigurado() {
        GoogleOAuthAdapter configurado = adaptador(CLIENT_ID, "s", decoderCon(c -> { }));
        assertThrows(CredencialesIncorrectasException.class, () -> configurado.verificarIdToken(null));
        assertThrows(CredencialesIncorrectasException.class, () -> configurado.verificarIdToken("  "));

        GoogleOAuthAdapter sinCliente = adaptador(null, null, null);
        assertThrows(ServicioExternoNoDisponibleException.class, () -> sinCliente.verificarIdToken("token"));
        GoogleOAuthAdapter clienteEnBlanco = adaptador(" , ", "s", null);
        assertThrows(ServicioExternoNoDisponibleException.class, () -> clienteEnBlanco.verificarIdToken("token"));
    }

    @ParameterizedTest(name = "code=[{0}] redirect=[{1}]")
    @CsvSource(value = {"NULL,https://app/cb", "' ',https://app/cb", "abc,NULL", "abc,' '"}, nullValues = "NULL")
    void intercambioSinCodigoORedirectEsInvalido(String code, String redirect) {
        GoogleOAuthAdapter adapter = adaptador(CLIENT_ID, "s", null);
        assertThrows(IllegalArgumentException.class, () -> adapter.intercambiarCodigoPorIdToken(code, redirect));
    }

    @Test
    void intercambioSinClienteConfidencialNoLlamaAGoogle() {
        GoogleOAuthAdapter sinSecreto = adaptador(CLIENT_ID, " ", null);
        assertThrows(ServicioExternoNoDisponibleException.class,
                () -> sinSecreto.intercambiarCodigoPorIdToken("abc", "https://app/cb"));
        GoogleOAuthAdapter sinCliente = adaptador("", "secreto", null);
        assertThrows(ServicioExternoNoDisponibleException.class,
                () -> sinCliente.intercambiarCodigoPorIdToken("abc", "https://app/cb"));
    }
}
