package edu.dosw.styleradar.auth.application.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.domain.model.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class JwtServiceImpl implements JwtService {

    public static final int MIN_SECRET_KEY_BYTES = 32;
    private static final String DISALLOWED_EXAMPLE_FINGERPRINT =
            "2cf5e6ec387461b4bf954f587ad4d957753fcbc48bf892b5e49996b90cf3b476";

    private final SecretKey secretKey;
    private final Duration duration;

    public JwtServiceImpl(String secretBase64, String duration) {
        this(secretBase64, duration, null);
    }

    @Autowired
    public JwtServiceImpl(
            @Value("${styleradar.security.jwt.secret-base64}") String secretBase64,
            @Value("${styleradar.security.jwt.duration:PT24H}") String duration,
            Environment environment) {

        this.secretKey = validarYConstruirClave(secretBase64, environment);
        this.duration = Duration.parse(duration);
    }

    private SecretKey validarYConstruirClave(String secretBase64, Environment environment) {
        if (secretBase64 == null || secretBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "Falla criptográfica (OWASP A04:2025): la clave secreta JWT (JWT_SECRET_BASE64) es obligatoria.");
        }

        String trimmed = secretBase64.trim();
        if (environment != null
                && environment.acceptsProfiles(Profiles.of("prod", "qa"))
                && esHuellaDeEjemplo(trimmed)) {
            throw new IllegalStateException(
                    "Falla criptográfica (OWASP A04:2025): no se permite la clave JWT de ejemplo en perfiles qa/prod.");
        }

        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(trimmed);
        } catch (DecodingException | IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Falla criptográfica (OWASP A04:2025): JWT_SECRET_BASE64 no tiene formato Base64 válido.", ex);
        }

        if (keyBytes.length < MIN_SECRET_KEY_BYTES) {
            throw new IllegalArgumentException(
                    "Falla criptográfica (OWASP A04:2025): la clave secreta JWT debe tener al menos 256 bits (32 bytes).");
        }

        return Keys.hmacShaKeyFor(keyBytes);
    }

    private static boolean esHuellaDeEjemplo(String rawValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return DISALLOWED_EXAMPLE_FINGERPRINT.equals(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo SHA-256 no disponible en la JVM.", ex);
        }
    }

    @Override
    public String generarToken(Long usuarioId, Set<Rol> roles) {
        Instant ahora = Instant.now();
        Instant expiracion = ahora.plus(duration);

        return Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .claim("roles", roles.stream().map(Enum::name).toList())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(expiracion))
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    @Override
    public Claims validarToken(String token) {
        return extraerClaims(token);
    }

    @Override
    public Long obtenerUsuarioId(String token) {
        try {
            Claims claims = extraerClaims(token);

            if (claims.getSubject() == null) {
                return null;
            }

            return Long.valueOf(claims.getSubject());

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("No se pudo obtener el usuario del token");
            return null;
        }
    }

    @Override
    public Set<Rol> obtenerRoles(String token) {
        try {
            Claims claims = extraerClaims(token);
            List<?> rawRoles = claims.get("roles", List.class);

            if (rawRoles == null || rawRoles.isEmpty()) {
                return Set.of();
            }

            Set<Rol> roles = new HashSet<>();

            for (Object r : rawRoles) {
                try {
                    roles.add(Rol.valueOf(String.valueOf(r)));
                } catch (IllegalArgumentException e) {
                    log.warn("Rol desconocido en token JWT ignorado");
                }
            }

            return roles;

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("No se pudieron obtener los roles del token");
            return Set.of();
        }
    }

    @Override
    public long obtenerTiempoRestanteSegundos(String token) {
        try {
            Claims claims = extraerClaims(token);

            if (claims.getExpiration() == null) {
                return 0;
            }

            long diferencia = claims.getExpiration().getTime()
                    - System.currentTimeMillis();

            return Math.max(0, diferencia / 1000);

        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
    }

    private Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
