package edu.dosw.styleradar.auth.application.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.domain.model.Rol;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public final class JwtServiceImpl implements JwtService {

    private final SecretKey secretKey;
    private final Duration duration;

    public JwtServiceImpl(
            @Value("${styleradar.security.jwt.secret-base64}") String secretBase64,
            @Value("${styleradar.security.jwt.duration:PT24H}") String duration) {

        this.secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        this.duration = Duration.parse(duration);
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
            log.debug("No se pudo obtener el usuario del token: {}", e.getMessage());
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
                    log.warn("Rol desconocido en token JWT: {}", r);
                }
            }

            return roles;

        } catch (JwtException | IllegalArgumentException e) {
            log.debug("No se pudieron obtener los roles: {}", e.getMessage());
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
