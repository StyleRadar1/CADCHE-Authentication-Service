package edu.dosw.styleradar.auth.infrastructure.adapter.out.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import edu.dosw.styleradar.auth.application.service.GoogleIdentityClaims;
import edu.dosw.styleradar.auth.domain.exception.CredencialesIncorrectasException;
import edu.dosw.styleradar.auth.domain.exception.ServicioExternoNoDisponibleException;
import io.jsonwebtoken.Jwts;

@DisplayName("Pruebas Criptográficas y de Integración de GoogleOAuthAdapter")
class GoogleOAuthAdapterTest {

    private static final String TEST_CLIENT_ID = "styleradar-web-client.apps.googleusercontent.com";
    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";

    private static KeyPair googleKeyPair;
    private static KeyPair attackerKeyPair;

    private MockRestServiceServer mockServer;
    private RestClient.Builder restClientBuilder;
    private GoogleOAuthAdapter adapter;
    private String runtimeConfigCredential;

    @BeforeAll
    static void generateRsaKeyPairs() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        googleKeyPair = generator.generateKeyPair();
        attackerKeyPair = generator.generateKeyPair();
    }

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        runtimeConfigCredential = "runtime-fixture-" + UUID.randomUUID();
        NimbusJwtDecoder rsaDecoder = NimbusJwtDecoder
                .withPublicKey((RSAPublicKey) googleKeyPair.getPublic())
                .build();

        adapter = new GoogleOAuthAdapter(
                restClientBuilder,
                TEST_CLIENT_ID,
                runtimeConfigCredential,
                GOOGLE_TOKEN_URL,
                GOOGLE_JWKS_URL,
                new MockEnvironment(),
                rsaDecoder);
    }

    @Test
    @DisplayName("Debe verificar ID Token RS256 válido con issuer https://accounts.google.com y accounts.google.com")
    void verificarIdToken_conFirmaYClaimsValidos_debeRetornarClaims() {
        String tokenHttpsIssuer = signToken(
                googleKeyPair,
                "https://accounts.google.com",
                TEST_CLIENT_ID,
                Instant.now().plusSeconds(300),
                "google-user-01",
                "Cliente.Google@gmail.com",
                true,
                "Mateo Calle");

        GoogleIdentityClaims claims = adapter.verificarIdToken(tokenHttpsIssuer);
        assertEquals("google-user-01", claims.subject());
        assertEquals("cliente.google@gmail.com", claims.email());
        assertTrue(claims.emailVerified());
        assertEquals("Mateo Calle", claims.name());

        String tokenPlainIssuer = signToken(
                googleKeyPair,
                "accounts.google.com",
                TEST_CLIENT_ID,
                Instant.now().plusSeconds(300),
                "google-user-02",
                "nora@gmail.com",
                true,
                "Nora");

        GoogleIdentityClaims claimsPlain = adapter.verificarIdToken(tokenPlainIssuer);
        assertEquals("nora@gmail.com", claimsPlain.email());
    }

    @Test
    @DisplayName("Debe rechazar firma inválida, token expirado, issuer erróneo, audiencia incorrecta y email no verificado")
    void verificarIdToken_conClaimsOFirmaInvalidos_debeLanzarCredencialesIncorrectas() {
        String wrongSignature = signToken(
                attackerKeyPair,
                "https://accounts.google.com",
                TEST_CLIENT_ID,
                Instant.now().plusSeconds(300),
                "sub-1",
                "user@gmail.com",
                true,
                "User");
        assertThrows(CredencialesIncorrectasException.class, () -> adapter.verificarIdToken(wrongSignature));

        String expiredToken = signToken(
                googleKeyPair,
                "https://accounts.google.com",
                TEST_CLIENT_ID,
                Instant.now().minusSeconds(120),
                "sub-1",
                "user@gmail.com",
                true,
                "User");
        assertThrows(CredencialesIncorrectasException.class, () -> adapter.verificarIdToken(expiredToken));

        String wrongIssuer = signToken(
                googleKeyPair,
                "https://idp-no-autorizado.ejemplo.com",
                TEST_CLIENT_ID,
                Instant.now().plusSeconds(300),
                "sub-1",
                "user@gmail.com",
                true,
                "User");
        assertThrows(CredencialesIncorrectasException.class, () -> adapter.verificarIdToken(wrongIssuer));

        String wrongAudience = signToken(
                googleKeyPair,
                "https://accounts.google.com",
                "otro-cliente-google.apps.googleusercontent.com",
                Instant.now().plusSeconds(300),
                "sub-1",
                "user@gmail.com",
                true,
                "User");
        assertThrows(CredencialesIncorrectasException.class, () -> adapter.verificarIdToken(wrongAudience));

        String unverifiedEmail = signToken(
                googleKeyPair,
                "https://accounts.google.com",
                TEST_CLIENT_ID,
                Instant.now().plusSeconds(300),
                "sub-1",
                "user@gmail.com",
                false,
                "User");
        assertThrows(CredencialesIncorrectasException.class, () -> adapter.verificarIdToken(unverifiedEmail));

        assertThrows(CredencialesIncorrectasException.class, () -> adapter.verificarIdToken("  "));
    }

    @Test
    @DisplayName("Debe mapear error de conectividad JWKS a 503 ServicioExternoNoDisponibleException")
    void verificarIdToken_cuandoFallaRedJwks_debeFallarCerradoCon503() {
        GoogleOAuthAdapter offlineJwksAdapter = new GoogleOAuthAdapter(
                restClientBuilder,
                TEST_CLIENT_ID,
                runtimeConfigCredential,
                GOOGLE_TOKEN_URL,
                GOOGLE_JWKS_URL,
                new MockEnvironment(),
                token -> {
                    throw new JwtException("JWKS unreachable", new IOException("Connection reset"));
                });

        assertThrows(
                ServicioExternoNoDisponibleException.class,
                () -> offlineJwksAdapter.verificarIdToken("token.con.error.red"));
    }

    @Test
    @DisplayName("Debe intercambiar authorization code por id_token contra Google Token Endpoint")
    void intercambiarCodigoPorIdToken_conRespuestaExitosa_debeRetornarIdToken() {
        String redirectUri = "http://localhost:8100/api/v1/auth/oauth2/google/callback";

        mockServer.expect(requestTo(GOOGLE_TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andRespond(withSuccess(
                        """
                        {
                            "access_token": "ya29.ejemplo",
                            "id_token": "google.id.token.valido",
                            "token_type": "Bearer",
                            "expires_in": 3599
                        }
                        """,
                        MediaType.APPLICATION_JSON));

        String idToken = adapter.intercambiarCodigoPorIdToken("google-code-abc", redirectUri);
        assertEquals("google.id.token.valido", idToken);
        mockServer.verify();
    }

    @Test
    @DisplayName("Debe mapear rechazo 4xx, ausencia de id_token y fallos de red/5xx al intercambiar code")
    void intercambiarCodigoPorIdToken_conErrores_debeMapearCorrectamente() {
        String redirectUri = "http://localhost:8100/api/v1/auth/oauth2/google/callback";

        mockServer.expect(requestTo(GOOGLE_TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThrows(
                CredencialesIncorrectasException.class,
                () -> adapter.intercambiarCodigoPorIdToken("bad-code", redirectUri));

        mockServer.reset();
        mockServer.expect(requestTo(GOOGLE_TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"only-access\"}", MediaType.APPLICATION_JSON));
        assertThrows(
                CredencialesIncorrectasException.class,
                () -> adapter.intercambiarCodigoPorIdToken("no-oidc-code", redirectUri));

        mockServer.reset();
        mockServer.expect(requestTo(GOOGLE_TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThrows(
                ServicioExternoNoDisponibleException.class,
                () -> adapter.intercambiarCodigoPorIdToken("code-502", redirectUri));

        mockServer.reset();
        mockServer.expect(requestTo(GOOGLE_TOKEN_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new ResourceAccessException("Google timeout");
                });
        assertThrows(
                ServicioExternoNoDisponibleException.class,
                () -> adapter.intercambiarCodigoPorIdToken("code-timeout", redirectUri));
    }

    @Test
    @DisplayName("Debe rechazar URLs de metadatos cloud o esquema inseguro en producción (SSRF OWASP A01:2025)")
    void constructor_conUrlSsrfOHttpEnProd_debeRechazar() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new GoogleOAuthAdapter(
                        restClientBuilder,
                        TEST_CLIENT_ID,
                        runtimeConfigCredential,
                        "http://169.254.169.254/latest/meta-data",
                        GOOGLE_JWKS_URL,
                        new MockEnvironment()));

        MockEnvironment prodEnv = new MockEnvironment();
        prodEnv.setActiveProfiles("prod");
        assertThrows(
                IllegalArgumentException.class,
                () -> new GoogleOAuthAdapter(
                        restClientBuilder,
                        TEST_CLIENT_ID,
                        runtimeConfigCredential,
                        "http://oauth2.googleapis.com/token",
                        GOOGLE_JWKS_URL,
                        prodEnv));
    }

    private static String signToken(
            KeyPair signingKey,
            String issuer,
            String audience,
            Instant expiration,
            String subject,
            String email,
            boolean emailVerified,
            String name) {
        return Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .claim("aud", List.of(audience))
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("name", name)
                .issuedAt(Date.from(Instant.now().minusSeconds(10)))
                .expiration(Date.from(expiration))
                .signWith(signingKey.getPrivate())
                .compact();
    }
}

