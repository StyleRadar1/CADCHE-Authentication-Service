package edu.dosw.styleradar.auth.application.service.impl;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import edu.dosw.styleradar.auth.application.service.JwtService;
import edu.dosw.styleradar.auth.domain.model.Rol;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtServiceImpl implements JwtService {

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
}
